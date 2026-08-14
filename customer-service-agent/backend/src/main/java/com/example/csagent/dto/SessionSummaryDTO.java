package com.example.csagent.dto;

import com.example.csagent.model.Conversation;
import com.example.csagent.model.ConversationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表项（含消息数、状态等概览信息）。 */
@Data
public class SessionSummaryDTO {
    private String id;
    private String title;
    private ConversationStatus status;
    private String intent;
    private boolean escalated;
    private int messageCount;
    private Integer satisfactionScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SessionSummaryDTO from(Conversation c, int messageCount) {
        SessionSummaryDTO d = new SessionSummaryDTO();
        d.setId(c.getId());
        d.setTitle(c.getTitle());
        d.setStatus(c.getStatus());
        d.setIntent(c.getIntent());
        d.setEscalated(c.isEscalated());
        d.setMessageCount(messageCount);
        d.setSatisfactionScore(c.getSatisfactionScore());
        d.setCreatedAt(c.getCreatedAt());
        d.setUpdatedAt(c.getUpdatedAt());
        return d;
    }
}
