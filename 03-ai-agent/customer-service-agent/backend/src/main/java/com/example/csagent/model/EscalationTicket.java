package com.example.csagent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转人工工单。当 Agent 无法解决或用户主动要求时创建，供人工客服跟进。
 */
@Data
@Entity
@Table(name = "escalation_tickets", indexes = {
        @Index(name = "idx_esc_conv", columnList = "conversation_id")
})
public class EscalationTicket {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    /** 触发原因：UNKNOWN_INTENT / LOW_CONFIDENCE / USER_REQUEST / FAQ_MISS */
    @Column(nullable = false, length = 30)
    private String reason;

    /** 转人工时的意图（可能为 UNKNOWN） */
    @Column(length = 30)
    private String intent;

    /** 工单状态：PENDING / PROCESSING / RESOLVED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.PENDING;

    /** 处理人 */
    @Column(length = 60)
    private String assignee;

    /** 用户原始诉求摘要 */
    @Lob
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
