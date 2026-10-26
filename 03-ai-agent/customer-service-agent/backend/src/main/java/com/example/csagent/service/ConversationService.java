package com.example.csagent.service;

import com.example.csagent.dto.Intent;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.ConversationStatus;
import com.example.csagent.model.Message;
import com.example.csagent.model.MessageRole;
import com.example.csagent.repository.ConversationRepository;
import com.example.csagent.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 会话与消息的持久化服务（多轮对话历史）。 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public Conversation getOrCreate(String conversationId, String firstUserText) {
        if (conversationId != null) {
            Conversation existing = conversationRepository.findById(conversationId).orElse(null);
            if (existing != null) return existing;
        }
        Conversation conv = new Conversation();
        conv.setId(UUID.randomUUID().toString());
        conv.setTitle(truncate(firstUserText, 30));
        conv.setStatus(ConversationStatus.ACTIVE);
        return conversationRepository.save(conv);
    }

    public Conversation get(String id) {
        return conversationRepository.findById(id).orElse(null);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(java.util.stream.Collectors.toList());
    }

    public int countMessages(String conversationId) {
        return messageRepository.countByConversationId(conversationId);
    }

    @Transactional
    public Message saveUserMessage(Conversation conv, String text, Intent intent) {
        Message m = new Message();
        m.setId(UUID.randomUUID().toString());
        m.setConversationId(conv.getId());
        m.setRole(MessageRole.USER);
        m.setContent(text);
        m.setIntent(intent == null ? null : intent.name());
        m.setSeq(messageRepository.maxSeq(conv.getId()) + 1);
        return messageRepository.save(m);
    }

    @Transactional
    public Message saveAssistantMessage(Conversation conv, String text, Intent intent,
                                        String faqIds, boolean escalated) {
        Message m = new Message();
        m.setId(UUID.randomUUID().toString());
        m.setConversationId(conv.getId());
        m.setRole(MessageRole.ASSISTANT);
        m.setContent(text);
        m.setIntent(intent == null ? null : intent.name());
        m.setFaqIds(faqIds);
        m.setEscalated(escalated);
        m.setSeq(messageRepository.maxSeq(conv.getId()) + 1);
        return messageRepository.save(m);
    }

    @Transactional
    public void updateStatus(Conversation conv, ConversationStatus status) {
        conv.setStatus(status);
        conversationRepository.save(conv);
    }

    @Transactional
    public void markEscalated(Conversation conv) {
        conv.setEscalated(true);
        conv.setStatus(ConversationStatus.ESCALATED);
        conversationRepository.save(conv);
    }

    public List<Message> history(String conversationId) {
        return messageRepository.findByConversationIdOrderBySeqAsc(conversationId);
    }

    @Transactional
    public void deleteCascade(String conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    private String truncate(String s, int max) {
        if (s == null) return "新对话";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
