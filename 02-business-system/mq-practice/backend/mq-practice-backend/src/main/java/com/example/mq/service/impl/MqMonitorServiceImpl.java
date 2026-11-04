package com.example.mq.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mq.common.api.PageResult;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.config.MqProperties;
import com.example.mq.dto.DlqMessageView;
import com.example.mq.dto.MqOverviewView;
import com.example.mq.dto.QueueInfoView;
import com.example.mq.entity.MqIdempotentLog;
import com.example.mq.entity.MqMessageLog;
import com.example.mq.mapper.MqIdempotentLogMapper;
import com.example.mq.mapper.MqMessageLogMapper;
import com.example.mq.mq.MessageSender;
import com.example.mq.service.MqMessageLogService;
import com.example.mq.service.MqMonitorService;
import com.example.mq.service.OrderService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MQ 监控实现。
 * <p>
 * <b>关于死信查看的一个重要设计</b>：死信队列不能用 {@code @RabbitListener} 去"看"，
 * 因为那等于把它消费掉。正确姿势是用 {@code basicGet(autoAck=false)} 取出 →
 * 读取内容 → {@code basicNack(requeue=true)} 放回。这样既能看到，又不会丢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqMonitorServiceImpl implements MqMonitorService {

    /** 队列 → 角色说明（顺序即展示顺序） */
    private static final Map<String, String> QUEUE_ROLES = buildQueueRoles();

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final MqMessageLogService messageLogService;
    private final MqMessageLogMapper messageLogMapper;
    private final MqIdempotentLogMapper idempotentLogMapper;
    private final OrderService orderService;
    private final MessageSender messageSender;
    private final MqProperties mqProperties;

    private static Map<String, String> buildQueueRoles() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put(MqConstants.ORDER_CREATE_QUEUE, "场景一｜异步落单消费者");
        roles.put(MqConstants.ORDER_CREATE_RETRY_QUEUE, "场景一｜失败重试（TTL 后回投）");
        roles.put(MqConstants.ORDER_DELAY_QUEUE, "场景二｜长延迟队列（无消费者，到期转死信）");
        roles.put(MqConstants.ORDER_DELAY_SHORT_QUEUE, "场景二｜短延迟队列（≤60s，演示用）");
        roles.put(MqConstants.ORDER_CANCEL_QUEUE, "场景二｜超时取消消费者");
        roles.put(MqConstants.ORDER_CANCEL_RETRY_QUEUE, "场景二｜失败重试");
        roles.put(MqConstants.PRODUCT_CACHE_QUEUE, "场景三｜缓存同步消费者");
        roles.put(MqConstants.PRODUCT_SEARCH_QUEUE, "场景三｜索引同步消费者");
        roles.put(MqConstants.PRODUCT_CACHE_RETRY_QUEUE, "场景三｜缓存同步重试");
        roles.put(MqConstants.PRODUCT_SEARCH_RETRY_QUEUE, "场景三｜索引同步重试");
        roles.put(MqConstants.DLQ_QUEUE, "公共｜死信队列（人工补偿）");
        return roles;
    }

    @Override
    public List<QueueInfoView> queues() {
        List<QueueInfoView> list = new ArrayList<>(QUEUE_ROLES.size());
        for (Map.Entry<String, String> entry : QUEUE_ROLES.entrySet()) {
            long messageCount = 0L;
            long consumerCount = 0L;
            try {
                QueueInformation info = amqpAdmin.getQueueInfo(entry.getKey());
                if (info != null) {
                    messageCount = info.getMessageCount();
                    consumerCount = info.getConsumerCount();
                }
            } catch (Exception e) {
                log.debug("[MONITOR] 读取队列 {} 失败：{}", entry.getKey(), e.getMessage());
            }
            list.add(new QueueInfoView(entry.getKey(), messageCount, consumerCount, entry.getValue()));
        }
        return list;
    }

    @Override
    public MqOverviewView overview() {
        List<QueueInfoView> queues = queues();
        long dlqCount = queues.stream()
                .filter(q -> MqConstants.DLQ_QUEUE.equals(q.getName()))
                .mapToLong(QueueInfoView::getMessageCount)
                .findFirst().orElse(0L);

        Map<String, Long> orderCounts = orderService.countByStatus();
        String brokerError = checkBroker();

        return MqOverviewView.builder()
                .queues(queues)
                .totalSent(messageLogService.count())
                .confirmedCount(messageLogMapper.countByStatus(MqMessageLog.STATUS_CONFIRMED))
                .returnedCount(messageLogMapper.countByStatus(MqMessageLog.STATUS_RETURNED))
                .failedCount(messageLogMapper.countByStatus(MqMessageLog.STATUS_FAILED))
                .idempotentRecords(idempotentLogMapper.selectCount(null))
                .orderQueuing(orderCounts.getOrDefault("QUEUING", 0L))
                .orderUnpaid(orderCounts.getOrDefault("UNPAID", 0L))
                .orderPaid(orderCounts.getOrDefault("PAID", 0L))
                .orderCancelled(orderCounts.getOrDefault("CANCELLED", 0L))
                .orderFailed(orderCounts.getOrDefault("FAILED", 0L))
                .dlqCount(dlqCount)
                .idempotentEnabled(mqProperties.isIdempotentEnabled())
                .delayMode(mqProperties.getDelayMode())
                .brokerAvailable(brokerError == null)
                .brokerMessage(brokerError == null ? "RabbitMQ 连接正常" : brokerError)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public PageResult<MqMessageLog> messages(String scene, String status, long current, long size) {
        Page<MqMessageLog> page = new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100));
        IPage<MqMessageLog> result = messageLogService.page(page,
                Wrappers.<MqMessageLog>lambdaQuery()
                        .eq(scene != null && !scene.isBlank(), MqMessageLog::getScene, scene)
                        .eq(status != null && !status.isBlank(), MqMessageLog::getStatus, status)
                        .orderByDesc(MqMessageLog::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PageResult<MqIdempotentLog> idempotentLogs(String scene, long current, long size) {
        Page<MqIdempotentLog> page = new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100));
        IPage<MqIdempotentLog> result = idempotentLogMapper.selectPage(page,
                Wrappers.<MqIdempotentLog>lambdaQuery()
                        .eq(scene != null && !scene.isBlank(), MqIdempotentLog::getScene, scene)
                        .orderByDesc(MqIdempotentLog::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<DlqMessageView> peekDlq(int limit) {
        int max = Math.min(Math.max(limit, 1), 50);
        try {
            return rabbitTemplate.execute((ChannelCallback<List<DlqMessageView>>) channel -> {
                List<GetResponse> responses = new ArrayList<>();
                GetResponse response;
                // 先全部取出（autoAck=false），再统一放回，避免 requeue 后又被同一轮取到
                while (responses.size() < max
                        && (response = channel.basicGet(MqConstants.DLQ_QUEUE, false)) != null) {
                    responses.add(response);
                }
                List<DlqMessageView> views = new ArrayList<>(responses.size());
                for (int i = 0; i < responses.size(); i++) {
                    views.add(toView(i + 1, responses.get(i)));
                }
                for (GetResponse item : responses) {
                    channel.basicNack(item.getEnvelope().getDeliveryTag(), false, true);
                }
                return views;
            });
        } catch (Exception e) {
            log.error("[MONITOR] 查看死信队列失败", e);
            return List.of();
        }
    }

    @Override
    public int replayDlq() {
        try {
            Integer count = rabbitTemplate.execute((ChannelCallback<Integer>) channel -> {
                int replayed = 0;
                GetResponse response;
                while ((response = channel.basicGet(MqConstants.DLQ_QUEUE, false)) != null) {
                    long tag = response.getEnvelope().getDeliveryTag();
                    try {
                        Target target = resolveTarget(response.getProps());
                        messageSender.republishRaw(target.exchange(), target.routingKey(),
                                response.getBody(),
                                response.getProps().getHeaders() == null
                                        ? Map.of() : response.getProps().getHeaders());
                        channel.basicAck(tag, false);
                        replayed++;
                        if (replayed >= 200) {
                            break;
                        }
                    } catch (Exception e) {
                        log.error("[MONITOR] 死信回放失败，消息放回队列", e);
                        channel.basicNack(tag, false, true);
                        break;
                    }
                }
                return replayed;
            });
            int result = count == null ? 0 : count;
            log.warn("[MONITOR] 死信回放完成，共 {} 条", result);
            return result;
        } catch (Exception e) {
            log.error("[MONITOR] 死信回放异常", e);
            return 0;
        }
    }

    /* ------------------------------- 内部方法 ------------------------------- */

    private String checkBroker() {
        try {
            Boolean open = rabbitTemplate.execute((ChannelCallback<Boolean>) Channel::isOpen);
            return Boolean.TRUE.equals(open) ? null : "RabbitMQ 通道未打开";
        } catch (Exception e) {
            return "RabbitMQ 不可用：" + e.getMessage();
        }
    }

    private DlqMessageView.DlqMessageViewBuilder baseBuilder() {
        return DlqMessageView.builder();
    }

    @SuppressWarnings("unchecked")
    private DlqMessageView toView(int index, GetResponse response) {
        AMQP.BasicProperties props = response.getProps();
        Map<String, Object> headers = props.getHeaders() == null ? Map.of() : props.getHeaders();

        String reason = null;
        String originalExchange = null;
        String originalRoutingKey = null;
        long deathCount = 0L;

        Object death = headers.get("x-death");
        if (death instanceof List<?> deaths && !deaths.isEmpty() && deaths.get(0) instanceof Map<?, ?> first) {
            reason = String.valueOf(first.get("reason"));
            originalExchange = String.valueOf(first.get("exchange"));
            Object count = first.get("count");
            if (count instanceof Number number) {
                deathCount = number.longValue();
            }
            Object routingKeys = first.get("routing-keys");
            if (routingKeys instanceof List<?> keys && !keys.isEmpty()) {
                originalRoutingKey = String.valueOf(keys.get(0));
            }
        }
        if (originalExchange == null || "null".equals(originalExchange)) {
            Target fallback = resolveTarget(props);
            originalExchange = fallback.exchange();
            originalRoutingKey = fallback.routingKey();
        }

        return baseBuilder()
                .index(index)
                .messageId(props.getMessageId() == null ? String.valueOf(headers.get(MqConstants.HEADER_MESSAGE_ID)) : props.getMessageId())
                .originalExchange(originalExchange == null || originalExchange.isEmpty()
                        ? "(默认交换机)" : originalExchange)
                .originalRoutingKey(originalRoutingKey)
                .reason(reason == null ? "unknown" : reason)
                .deathCount(deathCount)
                .scene(stringHeader(headers, MqConstants.HEADER_SCENE))
                .bizKey(stringHeader(headers, MqConstants.HEADER_BIZ_KEY))
                .payload(new String(response.getBody(), StandardCharsets.UTF_8))
                .build();
    }

    /** 从 x-death 或业务消息头推断回放目标 */
    @SuppressWarnings("unchecked")
    private Target resolveTarget(AMQP.BasicProperties props) {
        Map<String, Object> headers = props.getHeaders() == null ? Map.of() : props.getHeaders();
        Object death = headers.get("x-death");
        if (death instanceof List<?> deaths && !deaths.isEmpty() && deaths.get(0) instanceof Map<?, ?> first) {
            Object exchange = first.get("exchange");
            Object routingKeys = first.get("routing-keys");
            if (exchange != null && routingKeys instanceof List<?> keys && !keys.isEmpty()) {
                return new Target(String.valueOf(exchange), String.valueOf(keys.get(0)));
            }
        }
        // 兜底：按业务场景映射回原交换机
        String scene = stringHeader(headers, MqConstants.HEADER_SCENE);
        if (scene != null) {
            return switch (scene) {
                case "ORDER_CREATE" -> new Target(MqConstants.ORDER_EXCHANGE, MqConstants.ORDER_CREATE_ROUTING_KEY);
                case "ORDER_CANCEL" -> new Target(MqConstants.ORDER_CANCEL_EXCHANGE, MqConstants.ORDER_CANCEL_ROUTING_KEY);
                case "PRODUCT_CACHE_SYNC", "PRODUCT_SEARCH_SYNC", "PRODUCT_CHANGE" ->
                        new Target(MqConstants.PRODUCT_EXCHANGE, MqConstants.PRODUCT_CHANGED_ROUTING_KEY);
                default -> new Target(MqConstants.DLX_EXCHANGE, MqConstants.DLQ_ROUTING_KEY);
            };
        }
        return new Target(MqConstants.DLX_EXCHANGE, MqConstants.DLQ_ROUTING_KEY);
    }

    private String stringHeader(Map<String, Object> headers, String key) {
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private record Target(String exchange, String routingKey) {
    }

    /** 供定时任务/日志使用的时间基准 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
