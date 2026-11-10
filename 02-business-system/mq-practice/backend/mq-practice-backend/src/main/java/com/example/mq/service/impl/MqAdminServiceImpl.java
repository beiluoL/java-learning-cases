package com.example.mq.service.impl;

import com.example.mq.common.api.ResultCode;
import com.example.mq.common.exception.BusinessException;
import com.example.mq.config.MqAdminProperties;
import com.example.mq.dto.admin.*;
import com.example.mq.mq.admin.RabbitManagementClient;
import com.example.mq.service.MqAdminService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 运维台实现：只做「取数 + 裁剪 + 排序」，不缓存。
 * <p>
 * 为什么不缓存？运维数据的第一价值是"此刻真实"，缓存 5 秒就可能让人误判堆积是否在涨。
 * 页面侧用 3~5 秒轮询即可，broker 侧这点 QPS 完全可以承受。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqAdminServiceImpl implements MqAdminService {

    private static final int PEEK_MAX = 50;

    /** 允许从控制台手发的消息类型（白名单，避免任意类被反序列化） */
    private static final Map<String, String> MESSAGE_TYPES = Map.of(
            "ProductChangeMessage", "com.example.mq.common.message.ProductChangeMessage",
            "OrderCreateMessage", "com.example.mq.common.message.OrderCreateMessage",
            "OrderCancelMessage", "com.example.mq.common.message.OrderCancelMessage");

    private final RabbitManagementClient client;
    private final MqAdminProperties properties;
    private final ObjectMapper objectMapper;

    /* ============================== 状态 ============================== */

    @Override
    public AdminStatusView status() {
        AdminStatusView.AdminStatusViewBuilder builder = AdminStatusView.builder()
                .enabled(properties.isEnabled())
                .baseUrl(properties.getBaseUrl())
                .consoleUrl(properties.getConsoleUrl())
                .vhost(properties.getVhost())
                .username(properties.getUsername())
                .writeEnabled(properties.isWriteEnabled());

        if (!properties.isEnabled()) {
            return builder.ok(false).message("运维台已在配置中关闭（mq.practice.admin.enabled=false）").build();
        }

        JsonNode overview = client.tryGet("/api/overview");
        if (overview == null) {
            return builder.ok(false)
                    .message("无法连接 RabbitMQ 管理接口 " + properties.getBaseUrl()
                            + "，请确认 broker 已启动且 management 插件已开启")
                    .build();
        }

        JsonNode totals = overview.path("object_totals");
        JsonNode queueTotals = overview.path("queue_totals");
        return builder
                .ok(true)
                .version(overview.path("rabbitmq_version").asText("未知"))
                .clusterName(overview.path("cluster_name").asText("未知"))
                .erlangVersion(overview.path("erlang_version").asText(""))
                .queueCount(totals.path("queues").asLong())
                .exchangeCount(totals.path("exchanges").asLong())
                .consumerCount(totals.path("consumers").asLong())
                .connectionCount(totals.path("connections").asLong())
                .totalMessages(queueTotals.path("messages").asLong())
                .readyMessages(queueTotals.path("messages_ready").asLong())
                .unackedMessages(queueTotals.path("messages_unacknowledged").asLong())
                .message("连接正常")
                .build();
    }

    /* ============================== 队列 ============================== */

    @Override
    public List<AdminQueueView> queues() {
        JsonNode array = client.get("/api/queues/" + client.vhostSegment());
        List<AdminQueueView> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            Map<String, Object> arguments = toMap(node.get("arguments"));
            Object ttl = arguments.get("x-message-ttl");
            Object queueType = arguments.get("x-queue-type");
            String name = node.path("name").asText();
            result.add(AdminQueueView.builder()
                    .name(name)
                    .vhost(node.path("vhost").asText())
                    .type(queueType == null ? "classic" : String.valueOf(queueType))
                    .state(node.path("state").asText("unknown"))
                    .durable(node.path("durable").asBoolean())
                    .autoDelete(node.path("auto_delete").asBoolean())
                    .exclusive(node.path("exclusive").asBoolean())
                    .messages(node.path("messages").asLong())
                    .ready(node.path("messages_ready").asLong())
                    .unacked(node.path("messages_unacknowledged").asLong())
                    .consumers(node.path("consumers").asLong())
                    .publishRate(rate(node, "publish_details"))
                    .deliverRate(rate(node, "deliver_get_details"))
                    .ackRate(rate(node, "ack_details"))
                    .redeliverRate(rate(node, "redeliver_details"))
                    .messageBytes(node.path("message_bytes").asLong())
                    .arguments(arguments)
                    .messageTtlMs(ttl instanceof Number number ? number.longValue() : null)
                    .managedByProject(name.startsWith("mq."))
                    .build());
        }
        result.sort(Comparator.comparing(AdminQueueView::getName));
        return result;
    }

    /* ============================ 交换机 ============================ */

    @Override
    public List<AdminExchangeView> exchanges() {
        JsonNode array = client.get("/api/exchanges/" + client.vhostSegment());
        List<AdminExchangeView> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            result.add(AdminExchangeView.builder()
                    .name(node.path("name").asText())
                    .vhost(node.path("vhost").asText())
                    .type(node.path("type").asText())
                    .durable(node.path("durable").asBoolean())
                    .autoDelete(node.path("auto_delete").asBoolean())
                    .internal(node.path("internal").asBoolean())
                    .publishInRate(rate(node, "publish_in_details"))
                    .publishOutRate(rate(node, "publish_out_details"))
                    .arguments(toMap(node.get("arguments")))
                    .build());
        }
        result.sort(Comparator.comparing(AdminExchangeView::getName));
        return result;
    }

    /* ============================== 绑定 ============================== */

    @Override
    public List<AdminBindingView> bindings() {
        JsonNode array = client.get("/api/bindings/" + client.vhostSegment());
        List<AdminBindingView> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            result.add(AdminBindingView.builder()
                    .source(node.path("source").asText())
                    .destination(node.path("destination").asText())
                    .destinationType(node.path("destination_type").asText())
                    .routingKey(node.path("routing_key").asText())
                    .arguments(toMap(node.get("arguments")))
                    .build());
        }
        result.sort(Comparator.comparing(AdminBindingView::getSource)
                .thenComparing(AdminBindingView::getDestination));
        return result;
    }

    /* ============================= 消费者 ============================= */

    @Override
    public List<AdminConsumerView> consumers() {
        JsonNode array = client.get("/api/consumers/" + client.vhostSegment());
        List<AdminConsumerView> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            JsonNode channel = node.path("channel_details");
            result.add(AdminConsumerView.builder()
                    .queue(node.path("queue").path("name").asText())
                    .consumerTag(node.path("consumer_tag").asText())
                    .ackRequired(node.path("ack_required").asBoolean())
                    .prefetchCount(node.path("prefetch_count").asInt())
                    .activityStatus(node.path("activity_status").asText("unknown"))
                    .exclusive(node.path("exclusive").asBoolean())
                    .channel(channel.path("name").asText(""))
                    .peerHost(channel.path("peer_host").asText(""))
                    .connectionName(channel.path("connection_name").asText(""))
                    .build());
        }
        result.sort(Comparator.comparing(AdminConsumerView::getQueue));
        return result;
    }

    /* ============================== 连接 ============================== */

    @Override
    public List<AdminConnectionView> connections() {
        JsonNode array = client.get("/api/connections");
        List<AdminConnectionView> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            JsonNode clientProps = node.path("client_properties");
            String product = clientProps.path("product").asText("");
            String version = clientProps.path("version").asText("");
            String clientInfo = (product + " " + version).trim();
            result.add(AdminConnectionView.builder()
                    .name(node.path("name").asText())
                    .user(node.path("user").asText())
                    .peerHost(node.path("peer_host").asText())
                    .state(node.path("state").asText())
                    .channels(node.path("channels").asLong())
                    .recvRate(rate(node, "recv_oct_details"))
                    .sendRate(rate(node, "send_oct_details"))
                    .protocol(node.path("protocol").asText(""))
                    .ssl(node.path("ssl").asBoolean())
                    .clientInfo(clientInfo)
                    .build());
        }
        result.sort(Comparator.comparing(AdminConnectionView::getName));
        return result;
    }

    /* ============================ 消息查看 ============================ */

    @Override
    public List<PeekedMessageView> peek(String queue, int count) {
        int size = count <= 0 ? 10 : Math.min(count, PEEK_MAX);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", size);
        // 核心：ack_requeue_true = 读完放回，绝不消费
        body.put("ackmode", "ack_requeue_true");
        body.put("encoding", "auto");
        body.put("truncate", 200_000);

        JsonNode array = client.post(
                "/api/queues/" + client.vhostSegment() + "/" + client.segment(queue) + "/get", body);

        List<PeekedMessageView> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        int index = 0;
        for (JsonNode node : array) {
            index++;
            JsonNode props = node.path("properties");
            result.add(PeekedMessageView.builder()
                    .index(index)
                    .exchange(node.path("exchange").asText())
                    .routingKey(node.path("routing_key").asText())
                    .redelivered(node.path("redelivered").asBoolean())
                    .messageCount(node.path("message_count").asLong())
                    .payload(node.path("payload").asText())
                    .payloadBytes(node.path("payload_bytes").asLong())
                    .contentType(props.path("content_type").asText(""))
                    .headers(toMap(props.get("headers")))
                    .build());
        }
        return result;
    }

    /* ============================= 写操作 ============================= */

    @Override
    public int purge(String queue, String token) {
        guardWrite(token);
        if (!queue.startsWith("mq.")) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "运维台只允许清空本项目声明的队列（mq. 前缀），" + queue + " 请到原生控制台操作");
        }
        int before = 0;
        try {
            JsonNode info = client.get("/api/queues/" + client.vhostSegment() + "/" + client.segment(queue));
            before = info.path("messages").asInt();
        } catch (Exception ignored) {
            // 拿不到数量不影响清空
        }
        client.delete("/api/queues/" + client.vhostSegment() + "/" + client.segment(queue) + "/contents");
        log.warn("[MQ-ADMIN] 队列 {} 被清空，清理前 {} 条", queue, before);
        return before;
    }

    @Override
    public PublishResultView publish(PublishMessageRequest request, String token) {
        guardWrite(token);
        if (!StringUtils.hasText(request.getExchange())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "请填写交换机名（默认交换机不支持经此接口投递，可直接用原生控制台）");
        }
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("x-mq-scene", "MANUAL_PUBLISH");
        headers.put("x-mq-source", StringUtils.hasText(request.getSource()) ? request.getSource() : "mq-console");
        headers.put("x-mq-sent-at", System.currentTimeMillis());

        // 关键：消费端是强类型签名，必须带 __TypeId__ 才能反序列化成功
        if (StringUtils.hasText(request.getMessageType())) {
            String fqcn = MESSAGE_TYPES.get(request.getMessageType().trim());
            if (fqcn == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "不支持的消息类型：" + request.getMessageType() + "，可选 " + MESSAGE_TYPES.keySet());
            }
            headers.put("__TypeId__", fqcn);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("properties", Map.of("headers", headers, "delivery_mode", 2));
        body.put("routing_key", request.getRoutingKey());
        body.put("payload", request.getPayload());
        body.put("payload_encoding", "string");

        JsonNode result = client.post(
                "/api/exchanges/" + client.vhostSegment() + "/" + client.segment(request.getExchange()) + "/publish",
                body);
        boolean routed = result != null && result.path("routed").asBoolean(false);
        log.info("[MQ-ADMIN] 手动投递 exchange={} rk={} routed={}",
                request.getExchange(), request.getRoutingKey(), routed);
        return new PublishResultView(routed,
                routed ? "已路由到匹配队列" : "到达交换机但没有队列匹配该 routingKey，消息已被丢弃");
    }

    /* ============================== 工具 ============================== */

    /**
     * 写操作保护（fail-closed）：
     * 开关关闭 → 拒绝；口令为空 → 拒绝；口令不匹配 → 拒绝。
     * 只读接口不做口令校验 —— 生产环境应通过反向代理的 SSO 统一收口。
     */
    private void guardWrite(String token) {
        if (!properties.isWriteEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "运维台写操作已禁用（mq.practice.admin.write-enabled=false）");
        }
        String expected = properties.getWriteToken();
        if (!StringUtils.hasText(expected)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "未配置运维口令，写操作一律拒绝（mq.practice.admin.write-token 为空）");
        }
        if (!expected.equals(token)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "运维口令不正确");
        }
    }

    private double rate(JsonNode node, String field) {
        JsonNode value = node.path("message_stats").path(field).path("rate");
        return value.isNumber() ? value.asDouble() : 0d;
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
