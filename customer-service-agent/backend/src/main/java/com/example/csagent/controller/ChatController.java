package com.example.csagent.controller;

import com.example.csagent.dto.*;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.Message;
import com.example.csagent.service.AgentService;
import com.example.csagent.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 对话接口：以 SSE（text/event-stream）方式将 Agent 回复分片流式推送，
 * 事件类型含 init / intent / faq / escalate / text / done / error。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ConversationService conversationService;
    private final AgentService agentService;

    public ChatController(ConversationService conversationService, AgentService agentService) {
        this.conversationService = conversationService;
        this.agentService = agentService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String userMessage = req.getMessage();
        if (userMessage == null || userMessage.isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("message 不能为空"));
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Conversation conv = conversationService.getOrCreate(req.getConversationId(), userMessage);
                emitter.send(ChatEvent.of("init", Map.of(
                        "conversationId", conv.getId(),
                        "title", conv.getTitle())));

                AgentReply reply = agentService.handle(conv, userMessage, req.isForceEscalate());

                // 意图识别结果
                emitter.send(ChatEvent.of("intent", Map.of(
                        "intent", reply.getIntent().name(),
                        "label", reply.getIntent().getLabel(),
                        "confidence", round(reply.getIntentConfidence()),
                        "matchedKeywords", reply.getMatchedKeywords() == null ? "" : reply.getMatchedKeywords())));

                // 命中的 FAQ（管理/前端展示用）
                if (reply.getFaqs() != null && !reply.getFaqs().isEmpty()) {
                    List<Map<String, Object>> faqs = reply.getFaqs().stream().map(m -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("id", m.getItem().getId());
                        map.put("question", m.getItem().getQuestion());
                        map.put("answer", m.getItem().getAnswer());
                        map.put("score", round(m.getScore()));
                        return map;
                    }).toList();
                    emitter.send(ChatEvent.of("faq", faqs));
                }

                // 转人工
                if (reply.isEscalated()) {
                    emitter.send(ChatEvent.of("escalate", Map.of(
                            "ticketId", reply.getEscalateTicketId(),
                            "reason", reply.getEscalateReason())));
                }

                // 文本分片流式推送
                for (String chunk : chunk(reply.getContent())) {
                    emitter.send(ChatEvent.of("text", chunk));
                    Thread.sleep(18);
                }

                emitter.send(ChatEvent.of("done", Map.of(
                        "conversationId", conv.getId(),
                        "escalated", reply.isEscalated(),
                        "intent", reply.getIntent().name())));
                emitter.complete();
            } catch (Exception e) {
                log.error("[Chat] 处理失败", e);
                try {
                    emitter.send(ChatEvent.of("error", Map.of("message", e.getMessage())));
                } catch (Exception ignored) { }
                emitter.completeWithError(e);
            }
        });

        // 客户端断开时清理
        emitter.onCompletion(() -> log.debug("[Chat] SSE completed"));
        emitter.onError((e) -> log.debug("[Chat] SSE error: {}", e.getMessage()));
        return emitter;
    }

    /** 将文本切成小块以模拟流式输出。 */
    private List<String> chunk(String text) {
        if (text == null) return List.of();
        List<String> chunks = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int end = Math.min(i + 3, n);
            chunks.add(text.substring(i, end));
            i = end;
        }
        return chunks;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
