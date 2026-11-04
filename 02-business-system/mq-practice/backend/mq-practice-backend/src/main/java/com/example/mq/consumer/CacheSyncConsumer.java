package com.example.mq.consumer;

import com.example.mq.common.enums.ProductChangeType;
import com.example.mq.common.exception.BusinessRejectException;
import com.example.mq.common.message.ProductChangeMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import com.example.mq.config.RabbitMqConfig;
import com.example.mq.entity.MqProduct;
import com.example.mq.mapper.MqProductMapper;
import com.example.mq.mq.AbstractMqConsumer;
import com.example.mq.service.ProductCacheService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 【场景三】消费者 A：商品缓存同步（写 Redis）。
 * <p>
 * 它与 {@link SearchIndexConsumer} 绑定在同一个 exchange + 同一个 routingKey 上，
 * 各自持有一份消息副本，<b>完全独立</b>：
 * <ul>
 *   <li>本消费者慢或挂了，队列积压只发生在 {@code mq.product.cache.queue}，搜索侧不受影响；</li>
 *   <li>各自的死信、重试、并发度都可以单独调。</li>
 * </ul>
 * 这就是"发布订阅"相对"上游串行调用两个下游"的核心价值：故障隔离 + 独立伸缩。
 */
@Slf4j
@Component
public class CacheSyncConsumer extends AbstractMqConsumer<ProductChangeMessage> {

    private final MqProductMapper productMapper;
    private final ProductCacheService cacheService;

    public CacheSyncConsumer(MqProductMapper productMapper, ProductCacheService cacheService) {
        this.productMapper = productMapper;
        this.cacheService = cacheService;
    }

    @RabbitListener(
            queues = MqConstants.PRODUCT_CACHE_QUEUE,
            containerFactory = RabbitMqConfig.MANUAL_ACK_FACTORY,
            concurrency = "2-3")
    public void onMessage(ProductChangeMessage payload, Message message, Channel channel) throws IOException {
        handle(payload, message, channel);
    }

    @Override
    protected MqScene scene() {
        return MqScene.PRODUCT_CACHE_SYNC;
    }

    @Override
    protected String bizKeyOf(ProductChangeMessage payload) {
        // 幂等键带版本号：同一商品的不同版本必须都能处理，不能被幂等误杀
        return payload.getProductId() + ":v" + payload.getVersion();
    }

    @Override
    protected void doConsume(ProductChangeMessage payload, Message message) {
        MqProduct product = productMapper.selectById(payload.getProductId());
        if (product == null) {
            throw new BusinessRejectException("商品不存在，无法同步缓存 productId=" + payload.getProductId());
        }

        // 乱序保护：缓存里已有更新的版本就不覆盖
        Integer cachedVersion = cacheService.cachedVersion(payload.getProductId());
        if (cachedVersion != null && cachedVersion > payload.getVersion()) {
            log.warn("[CACHE-SYNC] 丢弃过期事件 productId={} msgVersion={} cacheVersion={}",
                    payload.getProductId(), payload.getVersion(), cachedVersion);
            return;
        }

        if (payload.getChangeType() == ProductChangeType.OFFLINE || product.getStatus() == 0) {
            cacheService.evict(payload.getProductId());
            log.info("[CACHE-SYNC] 商品下架，已删除缓存 productId={}", payload.getProductId());
        } else {
            cacheService.write(product);
            log.info("[CACHE-SYNC] 缓存已刷新 productId={} version={} price={}",
                    payload.getProductId(), product.getVersion(), product.getPrice());
        }
    }
}
