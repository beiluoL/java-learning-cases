package com.example.csagent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（对话）实体。一条会话对应一个用户的完整咨询过程（多轮）。
 */
@Data
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 200)
    private String title = "新对话";

    /** 会话状态：进行中 / 已转人工 / 已解决 / 已关闭 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    /** 识别出的主导意图：REFUND / ORDER_QUERY / TECH_SUPPORT / UNKNOWN / null */
    @Column(length = 30)
    private String intent;

    /** 是否已转人工 */
    @Column(nullable = false)
    private boolean escalated = false;

    /** 满意度评分（1-5），未评价为 null */
    @Column
    private Integer satisfactionScore;

    /** 满意度文字评价 */
    @Column(length = 500)
    private String satisfactionComment;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
