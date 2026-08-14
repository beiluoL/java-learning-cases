package com.example.csagent.controller;

import com.example.csagent.dto.SatisfactionRequest;
import com.example.csagent.model.Conversation;
import com.example.csagent.service.ConversationService;
import com.example.csagent.service.SatisfactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 满意度评价提交接口。 */
@RestController
@RequestMapping("/satisfaction")
public class SatisfactionController {

    private final SatisfactionService satisfactionService;
    private final ConversationService conversationService;

    public SatisfactionController(SatisfactionService satisfactionService,
                                  ConversationService conversationService) {
        this.satisfactionService = satisfactionService;
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody SatisfactionRequest req) {
        Conversation conv = conversationService.get(req.getConversationId());
        if (conv == null) return ResponseEntity.badRequest().body("会话不存在");
        satisfactionService.submit(conv, req);
        return ResponseEntity.ok().build();
    }
}
