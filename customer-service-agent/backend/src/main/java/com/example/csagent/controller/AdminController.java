package com.example.csagent.controller;

import com.example.csagent.dto.*;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.ConversationStatus;
import com.example.csagent.model.EscalationTicket;
import com.example.csagent.model.Message;
import com.example.csagent.repository.ConversationRepository;
import com.example.csagent.repository.EscalationTicketRepository;
import com.example.csagent.repository.MessageRepository;
import com.example.csagent.repository.SatisfactionRepository;
import com.example.csagent.service.SatisfactionService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/** 管理后台接口：对话记录检索 + 满意度/概览统计。 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final EscalationTicketRepository ticketRepository;
    private final SatisfactionRepository satisfactionRepository;
    private final SatisfactionService satisfactionService;

    public AdminController(ConversationRepository conversationRepository,
                           MessageRepository messageRepository,
                           EscalationTicketRepository ticketRepository,
                           SatisfactionRepository satisfactionRepository,
                           SatisfactionService satisfactionService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.satisfactionRepository = satisfactionRepository;
        this.satisfactionService = satisfactionService;
    }

    /** 对话记录列表（支持状态/意图/关键词过滤 + 分页）。 */
    @GetMapping("/conversations")
    public Map<String, Object> conversations(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String intent,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        List<Conversation> all = conversationRepository.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());

        List<Conversation> filtered = all.stream().filter(c -> {
            if (status != null && !status.isBlank()
                    && !c.getStatus().name().equalsIgnoreCase(status)) return false;
            if (intent != null && !intent.isBlank()
                    && !(c.getIntent() != null && c.getIntent().equalsIgnoreCase(intent))) return false;
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                boolean hit = (c.getTitle() != null && c.getTitle().toLowerCase().contains(kw))
                        || (c.getIntent() != null && c.getIntent().toLowerCase().contains(kw));
                if (!hit) {
                    boolean msgHit = messageRepository.findByConversationIdOrderBySeqAsc(c.getId()).stream()
                            .anyMatch(m -> m.getContent() != null && m.getContent().toLowerCase().contains(kw));
                    if (!msgHit) return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<SessionSummaryDTO> content = filtered.subList(from, to).stream()
                .map(c -> SessionSummaryDTO.from(c, messageRepository.countByConversationId(c.getId())))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 对话详情。 */
    @GetMapping("/conversations/{id}")
    public ConversationDetailDTO detail(@PathVariable String id) {
        Conversation conv = conversationRepository.findById(id).orElse(null);
        if (conv == null) throw new IllegalArgumentException("会话不存在: " + id);
        ConversationDetailDTO dto = new ConversationDetailDTO();
        dto.setConversation(conv);
        dto.setMessages(messageRepository.findByConversationIdOrderBySeqAsc(id));
        var tickets = ticketRepository.findByConversationIdOrderByCreatedAtDesc(id);
        dto.setTicket(tickets.isEmpty() ? null : tickets.get(0));
        return dto;
    }

    /** 概览统计。 */
    @GetMapping("/stats/overview")
    public Map<String, Object> overview() {
        long total = conversationRepository.count();
        long escalated = conversationRepository.countByEscalatedTrue();
        long resolved = conversationRepository.findAll().stream()
                .filter(c -> c.getStatus() == ConversationStatus.RESOLVED).count();
        long active = conversationRepository.findAll().stream()
                .filter(c -> c.getStatus() == ConversationStatus.ACTIVE).count();
        long totalMessages = messageRepository.count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalConversations", total);
        m.put("escalatedConversations", escalated);
        m.put("resolvedConversations", resolved);
        m.put("activeConversations", active);
        m.put("totalMessages", totalMessages);
        m.put("satisfactionAverage", round(satisfactionService.averageScore()));
        m.put("satisfactionCount", satisfactionService.count());
        return m;
    }

    /** 满意度统计。 */
    @GetMapping("/stats/satisfaction")
    public SatisfactionStatsDTO satisfaction() {
        SatisfactionStatsDTO dto = new SatisfactionStatsDTO();
        dto.setTotal(satisfactionService.count());
        dto.setAverage(round(satisfactionService.averageScore()));

        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        for (Object[] row : satisfactionRepository.scoreDistribution()) {
            int score = ((Number) row[0]).intValue();
            long cnt = ((Number) row[1]).longValue();
            dist.put(score, cnt);
        }
        dto.setDistribution(dist);

        List<SatisfactionStatsDTO.IntentStat> byIntent = satisfactionRepository.statsByIntent().stream()
                .map(row -> {
                    SatisfactionStatsDTO.IntentStat s = new SatisfactionStatsDTO.IntentStat();
                    s.setIntent(String.valueOf(row[0]));
                    s.setAverage(round(((Number) row[1]).doubleValue()));
                    s.setCount(((Number) row[2]).longValue());
                    return s;
                }).collect(Collectors.toList());
        dto.setByIntent(byIntent);

        dto.setEscalatedConversations(conversationRepository.countByEscalatedTrue());
        dto.setTotalConversations(conversationRepository.count());
        return dto;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
