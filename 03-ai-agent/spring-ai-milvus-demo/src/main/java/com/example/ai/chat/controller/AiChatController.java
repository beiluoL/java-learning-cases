package com.example.ai.chat.controller;

import com.example.ai.chat.dto.ChatDtos;
import com.example.ai.chat.service.AiChatService;
import com.example.ai.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 问答入口：只做 HTTP 与参数校验，业务编排全部在 AiChatService。 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatDtos.ChatResponseVO> chat(@Valid @RequestBody ChatDtos.ChatRequest request) {
        ChatDtos.ChatResponseVO data = chatService.chat(request);
        // 业务服务在 finally 中已清理 MDC，这里用 VO 自带的 traceId 保证统一响应可追溯
        return new ApiResponse<>(0, "success", data, data.traceId());
    }
}
