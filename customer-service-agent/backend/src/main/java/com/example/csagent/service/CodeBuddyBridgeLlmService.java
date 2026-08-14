package com.example.csagent.service;

import com.example.csagent.config.AppProperties;
import com.example.csagent.dto.AgentContext;
import com.example.csagent.dto.FaqMatch;
import com.example.csagent.dto.Intent;
import com.example.csagent.dto.IntentResult;
import com.example.csagent.model.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通过 HTTP 调用 CodeBuddy Agent 桥接服务（Node，封装 @tencent-ai/agent-sdk），
 * 获取大模型生成的自然语言回复。桥接不可达或出错时自动降级到本地规则引擎。
 *
 * 对应 skill codebuddy-chat-web 中 server/index.ts 的 SDK 调用模式，
 * 由 Node 侧负责鉴权（CODEBUDDY_API_KEY）与流式 query。
 */
@Service
public class CodeBuddyBridgeLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(CodeBuddyBridgeLlmService.class);

    private final AppProperties appProperties;
    private final LocalAgentService fallback;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CodeBuddyBridgeLlmService(AppProperties appProperties,
                                      LocalAgentService fallback) {
        this.appProperties = appProperties;
        this.fallback = fallback;
        this.restTemplate = new RestTemplate();
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(3000);
            setReadTimeout(60000);
        }});
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String generate(AgentContext ctx) {
        try {
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

            String url = appProperties.getCodebuddy().getBaseUrl().replaceAll("/$", "") + "/api/agent/complete";
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                BridgeResponse br = objectMapper.readValue(resp.getBody(), BridgeResponse.class);
                if (br.text != null && !br.text.isBlank()) {
                    return br.text;
                }
            }
            log.warn("[CodeBuddy] 桥接返回异常，降级到本地引擎");
        } catch (Exception e) {
            log.warn("[CodeBuddy] 调用桥接失败（{}），降级到本地引擎", e.getMessage());
        }
        return fallback.generate(ctx);
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
