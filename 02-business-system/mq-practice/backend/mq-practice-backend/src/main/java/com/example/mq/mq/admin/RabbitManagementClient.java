package com.example.mq.mq.admin;

import com.example.mq.common.api.ResultCode;
import com.example.mq.common.exception.BusinessException;
import com.example.mq.config.MqAdminProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ Management HTTP API 客户端（服务端专用）。
 *
 * <h3>为什么用 REST 而不是复用 AMQP 连接</h3>
 * AMQP 协议只能拿到「队列有多少条消息」这类运行时数据，拿不到速率、连接、通道、
 * 绑定拓扑和消息体。Management 插件把这些信息暴露成 REST + JSON，是官方支持的唯一稳定入口
 * （{@code rabbitmqctl} 更适合脚本，不适合给页面用）。
 *
 * <h3>几个实现细节</h3>
 * <ul>
 *   <li>vhost 是路径参数，{@code "/"} 必须编码成 {@code %2F}，否则会 404 —— 这是接 Management API 最常踩的坑；</li>
 *   <li>直接构造 {@link URI} 对象交给 RestClient，避免模板二次编码把 {@code %2F} 变成 {@code %252F}；</li>
 *   <li>连接超时与读取超时必须给（broker 假死时不能把 Web 线程一起拖住）。</li>
 * </ul>
 */
@Slf4j
@Component
public class RabbitManagementClient {

    private final MqAdminProperties properties;
    private final RestClient restClient;
    private final String baseUrl;

    public RabbitManagementClient(MqAdminProperties properties) {
        this.properties = properties;
        this.baseUrl = StringUtils.trimTrailingCharacter(properties.getBaseUrl(), '/');

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers ->
                        headers.setBasicAuth(properties.getUsername(), properties.getPassword()))
                .build();
    }

    /** {@code "/"} → {@code "%2F"}；其余按 path segment 规则编码 */
    public String vhostSegment() {
        return UriUtils.encodePathSegment(properties.getVhost(), StandardCharsets.UTF_8);
    }

    public String segment(String raw) {
        return UriUtils.encodePathSegment(raw, StandardCharsets.UTF_8);
    }

    public JsonNode get(String path) {
        return execute(() -> restClient.get().uri(uri(path)).retrieve().body(JsonNode.class), path);
    }

    public JsonNode post(String path, Object body) {
        return execute(() -> restClient.post()
                .uri(uri(path))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class), path);
    }

    public void delete(String path) {
        execute(() -> {
            restClient.delete().uri(uri(path)).retrieve().toBodilessEntity();
            return null;
        }, path);
    }

    /** 供 /status 用：不抛异常，返回 null 表示不可达 */
    public JsonNode tryGet(String path) {
        try {
            return get(path);
        } catch (Exception e) {
            log.debug("[MQ-ADMIN] 探测 {} 失败：{}", path, e.getMessage());
            return null;
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private JsonNode execute(java.util.function.Supplier<JsonNode> action, String path) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.MQ_ADMIN_DISABLED);
        }
        try {
            JsonNode node = action.get();
            log.debug("[MQ-ADMIN] {} 调用成功", path);
            return node;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.warn("[MQ-ADMIN] {} 返回 {}：{}", path, e.getStatusCode().value(), body);
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new BusinessException(ResultCode.MQ_ADMIN_ERROR,
                        "RabbitMQ 管理接口认证失败，请检查 mq.practice.admin.username/password "
                                + "（当前账号：" + properties.getUsername() + "）");
            }
            throw new BusinessException(ResultCode.MQ_ADMIN_ERROR,
                    "管理接口返回 " + e.getStatusCode().value() + "：" + shorten(body));
        } catch (ResourceAccessException e) {
            throw new BusinessException(ResultCode.MQ_ADMIN_ERROR,
                    "无法连接 RabbitMQ 管理接口 " + baseUrl + "，请确认 broker 已启动且 management 插件已开启（" + e.getMessage() + "）");
        }
    }

    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }
}
