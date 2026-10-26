package com.example.csagent.service;

import com.example.csagent.dto.Intent;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.EscalationTicket;
import com.example.csagent.model.TicketStatus;
import com.example.csagent.repository.EscalationTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 转人工工单服务。 */
@Service
public class EscalationService {

    private final EscalationTicketRepository ticketRepository;

    public EscalationService(EscalationTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public EscalationTicket create(Conversation conv, String reason, Intent intent, String summary) {
        EscalationTicket ticket = new EscalationTicket();
        ticket.setId("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        ticket.setConversationId(conv.getId());
        ticket.setReason(reason);
        ticket.setIntent(intent == null ? "UNKNOWN" : intent.name());
        ticket.setSummary(summary);
        ticket.setStatus(TicketStatus.PENDING);
        return ticketRepository.save(ticket);
    }

    public EscalationTicket findLatest(String conversationId) {
        var list = ticketRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        return list.isEmpty() ? null : list.get(0);
    }
}
