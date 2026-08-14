package com.example.csagent.service;

import com.example.csagent.dto.Intent;
import com.example.csagent.dto.SatisfactionRequest;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.ConversationStatus;
import com.example.csagent.model.Satisfaction;
import com.example.csagent.repository.SatisfactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 满意度评价服务。 */
@Service
public class SatisfactionService {

    private final SatisfactionRepository satisfactionRepository;

    public SatisfactionService(SatisfactionRepository satisfactionRepository) {
        this.satisfactionRepository = satisfactionRepository;
    }

    @Transactional
    public Satisfaction submit(Conversation conv, SatisfactionRequest req) {
        int score = Math.max(1, Math.min(5, req.getScore()));
        Satisfaction s = new Satisfaction();
        s.setId(UUID.randomUUID().toString());
        s.setConversationId(conv.getId());
        s.setScore(score);
        s.setComment(req.getComment());
        s.setIntent(conv.getIntent());
        Satisfaction saved = satisfactionRepository.save(s);

        // 同步到会话并标记已解决
        conv.setSatisfactionScore(score);
        conv.setSatisfactionComment(req.getComment());
        conv.setStatus(ConversationStatus.RESOLVED);
        return saved;
    }

    public List<Satisfaction> findByConversation(String conversationId) {
        return satisfactionRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
    }

    public Double averageScore() {
        Double avg = satisfactionRepository.averageScore();
        return avg == null ? 0.0 : avg;
    }

    public List<Object[]> distribution() {
        return satisfactionRepository.scoreDistribution();
    }

    public long count() {
        return satisfactionRepository.count();
    }
}
