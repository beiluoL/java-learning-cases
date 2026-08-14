package com.example.csagent.repository;

import com.example.csagent.model.EscalationTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EscalationTicketRepository extends JpaRepository<EscalationTicket, String> {
    List<EscalationTicket> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}
