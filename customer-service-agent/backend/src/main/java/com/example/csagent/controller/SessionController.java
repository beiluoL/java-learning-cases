package com.example.csagent.controller;

import com.example.csagent.dto.ConversationDetailDTO;
import com.example.csagent.dto.SessionSummaryDTO;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.EscalationTicket;
import com.example.csagent.model.Message;
import com.example.csagent.repository.EscalationTicketRepository;
import com.example.csagent.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** 会话（对话历史）接口，供客服端与管理端共用。 */
@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final ConversationService conversationService;
    private final EscalationTicketRepository ticketRepository;

    public SessionController(ConversationService conversationService,
                             EscalationTicketRepository ticketRepository) {
        this.conversationService = conversationService;
        this.ticketRepository = ticketRepository;
    }

    /** 会话列表（含消息数）。 */
    @GetMapping
    public List<SessionSummaryDTO> list() {
        return conversationService.getAllConversations().stream()
                .map(c -> SessionSummaryDTO.from(c, conversationService.countMessages(c.getId())))
                .collect(Collectors.toList());
    }

    /** 会话详情（消息 + 工单）。 */
    @GetMapping("/{id}")
    public ConversationDetailDTO detail(@PathVariable String id) {
        Conversation conv = conversationService.get(id);
        if (conv == null) throw new IllegalArgumentException("会话不存在: " + id);
        List<Message> messages = conversationService.history(id);
        EscalationTicket ticket = null;
        var tickets = ticketRepository.findByConversationIdOrderByCreatedAtDesc(id);
        if (!tickets.isEmpty()) ticket = tickets.get(0);
        ConversationDetailDTO dto = new ConversationDetailDTO();
        dto.setConversation(conv);
        dto.setMessages(messages);
        dto.setTicket(ticket);
        return dto;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        conversationService.deleteCascade(id);
    }
}
