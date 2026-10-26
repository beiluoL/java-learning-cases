package com.example.csagent.service;

import com.example.csagent.config.AppProperties;
import com.example.csagent.dto.AgentContext;
import com.example.csagent.dto.FaqMatch;
import com.example.csagent.dto.Intent;
import com.example.csagent.dto.IntentResult;
import com.example.csagent.model.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 通过 HTTP 调用 CodeBuddy Agent 桥接服务（Node，封装 @tencent-ai/agent-sdk），
 * 获取大模型生成的自然语言回复，并真正以「逐 token」方式流式回调给上层。
 *
 * 对应 skill codebuddy-chat-web 中 server/index.ts 的 SDK 调用模式：
 * 由 Node 侧负责鉴权（本机 CodeBuddy CN 登录态 / CODEBUDDY_API_KEY）与流式 query，
 * 本类负责把桥接返回的 SSE（{type:'token'}）实时转成 onToken 回调。
 * 桥接不可达 / 返回 error 时自动降级到本地规则引擎（逐 token 兜底）。
 */
@Service
public class CodeBuddyBridgeLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(CodeBuddyBridgeLlmService.class);

    private final AppProperties appProperties;
    private final LocalAgentService fallback;
    private final RestTemplate restTemplate;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CodeBuddyBridgeLlmService(AppProperties appProperties,
                                      LocalAgentService fallback) {
        this.appProperties = appProperties;
        this.fallback = fallback;
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {{
            setConnectTimeout(3000);
            setReadTimeout(60000);
        }});
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** 非流式：一次性拿全文（兜底 / 非 SSE 场景用）。 */
    @Override
    public String generate(AgentContext ctx) {
        try {
            BridgeRequest req = buildRequest(ctx);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<String> entity =
                    new org.springframework.http.HttpEntity<>(objectMapper.writeValueAsString(req), headers);
            String url = appProperties.getCodebuddy().getBaseUrl().replaceAll("/$", "") + "/api/agent/complete";
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                BridgeResponse br = objectMapper.readValue(resp.getBody(), BridgeResponse.class);
                if (br.text != null && !br.text.isBlank()) return br.text;
            }
            log.warn("[CodeBuddy] 桥接返回异常，降级到本地引擎");
        } catch (Exception e) {
            log.warn("[CodeBuddy] 调用桥接失败（{}），降级到本地引擎", e.getMessage());
        }
        return fallback.generate(ctx);
    }

    /** 真实流式：消费桥接 SSE，逐 token 回调；桥接报错则降级本地引擎流式。 */
    @Override
    public void streamGenerate(AgentContext ctx, Consumer<String> onToken) {
        try {
            BridgeRequest req = buildRequest(ctx);
            String json = objectMapper.writeValueAsString(req);
            String url = appProperties.getCodebuddy().getBaseUrl().replaceAll("/$", "") + "/api/agent/stream";
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(180))
                    .build();

            HttpResponse<java.io.InputStream> resp =
                    httpClient.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                log.warn("[CodeBuddy] 桥接 HTTP {}，降级本地引擎", resp.statusCode());
                fallback.streamGenerate(ctx, onToken);
                return;
            }

            AtomicBoolean errored = new AtomicBoolean(false);
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;
                    JsonNode node = objectMapper.readTree(data);
                    String type = node.path("type").asText();
                    if ("token".equals(type)) {
                        String t = node.path("text").asText("");
                        if (!t.isEmpty()) onToken.accept(t);
                    } else if ("error".equals(type)) {
                        errored.set(true);
                        break;
                    } else if ("done".equals(type)) {
                        break;
                    }
                }
            }
            if (errored.get()) {
                log.warn("[CodeBuddy] 桥接返回 error 事件，降级本地引擎");
                fallback.streamGenerate(ctx, onToken);
            }
        } catch (Exception e) {
            log.warn("[CodeBuddy] 流式调用桥接失败（{}），降级本地引擎", e.getMessage());
            fallback.streamGenerate(ctx, onToken);
        }
    }

    private BridgeRequest buildRequest(AgentContext ctx) {
        BridgeRequest req = new BridgeRequest();
        req.userMessage = ctx.getUserMessage();
        IntentResult ir = ctx.getIntent();
        req.intent = ir == null ? "UNKNOWN" : ir.getIntent().name();
        req.intentLabel = ir == null ? "未知" : ir.getIntent().getLabel();
        req.faqs = (ctx.getFaqs() == null ? List.<FaqMatch>of() : ctx.getFaqs()).stream()
                .map(m -> new BridgeFaq(m.getItem().getQuestion(), m.getItem().getAnswer()))
                .collect(Collectors.toList());
        req.history = (ctx.getHistory() == null ? List.<Message>of() : ctx.getHistory()).stream()
                .map(m -> new BridgeHistory(m.getRole().name().toLowerCase(), m.getContent()))
                .collect(Collectors.toList());
        return req;
    }

    // ===== 请求/响应 DTO =====
    static class BridgeRequest {
        public String userMessage;
        public String intent;
        public String intentLabel;
        public List<BridgeFaq> faqs;
        public List<BridgeHistory> history;
    }

    static class BridgeFaq {
        public String question;
        public String answer;
        BridgeFaq(String q, String a) { this.question = q; this.answer = a; }
    }

    static class BridgeHistory {
        public String role;
        public String content;
        BridgeHistory(String r, String c) { this.role = r; this.content = c; }
    }

    static class BridgeResponse {
        @JsonProperty("text")
        public String text;
    }
}
