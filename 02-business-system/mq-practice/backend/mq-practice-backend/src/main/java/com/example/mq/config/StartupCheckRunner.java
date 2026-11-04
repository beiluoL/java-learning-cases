package com.example.mq.config;

import com.example.mq.common.mq.MqConstants;
import com.example.mq.entity.MqStock;
import com.example.mq.mapper.MqStockMapper;
import com.example.mq.mq.RedisStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动自检：
 * <ol>
 *   <li>把数据库库存预热到 Redis（秒杀预扣的前提）；</li>
 *   <li>打印 MQ 拓扑与各队列当前积压，便于确认 broker 是否就绪、拓扑是否声明成功。</li>
 * </ol>
 * 注意：<b>broker 未就绪不能让应用启动失败</b>。真实部署里 MQ 与应用的启动顺序不确定，
 * 应用应当能起来、能重连，而不是直接崩掉（Boot 的 RabbitAdmin 会在连接恢复后自动补声明）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCheckRunner implements ApplicationRunner {

    private static final List<String> CHECK_QUEUES = List.of(
            MqConstants.ORDER_CREATE_QUEUE,
            MqConstants.ORDER_CREATE_RETRY_QUEUE,
            MqConstants.ORDER_DELAY_QUEUE,
            MqConstants.ORDER_CANCEL_QUEUE,
            MqConstants.PRODUCT_CACHE_QUEUE,
            MqConstants.PRODUCT_SEARCH_QUEUE,
            MqConstants.DLQ_QUEUE);

    private final MqStockMapper stockMapper;
    private final RedisStockService redisStockService;
    private final AmqpAdmin amqpAdmin;

    @Override
    public void run(ApplicationArguments args) {
        warmUpRedisStock();
        checkTopology();
    }

    private void warmUpRedisStock() {
        try {
            List<MqStock> stocks = stockMapper.selectList(null);
            for (MqStock stock : stocks) {
                redisStockService.initStock(stock.getProductId(), stock.getAvailable());
            }
            log.info("[STARTUP] 已预热 {} 个商品的秒杀库存到 Redis", stocks.size());
        } catch (Exception e) {
            log.error("[STARTUP] 秒杀库存预热失败（秒杀接口会降级为只走数据库）", e);
        }
    }

    private void checkTopology() {
        int ok = 0;
        for (String queue : CHECK_QUEUES) {
            try {
                QueueInformation info = amqpAdmin.getQueueInfo(queue);
                if (info != null) {
                    ok++;
                    log.info("[STARTUP] 队列 {} 消息数={} 消费者数={}",
                            info.getName(), info.getMessageCount(), info.getConsumerCount());
                }
            } catch (Exception e) {
                log.warn("[STARTUP] 队列 {} 尚未就绪：{}", queue, e.getMessage());
            }
        }
        if (ok == 0) {
            log.error("""
                    [STARTUP] RabbitMQ 不可用！
                      请先启动 broker：docker compose up -d rabbitmq
                      或检查 spring.rabbitmq.host/port/username/password 配置。
                      应用已启动，但投递会失败；broker 恢复后会自动重连并声明拓扑。
                    """);
        } else {
            log.info("[STARTUP] MQ 拓扑检查完成，{}/{} 个队列可用", ok, CHECK_QUEUES.size());
        }
    }
}
