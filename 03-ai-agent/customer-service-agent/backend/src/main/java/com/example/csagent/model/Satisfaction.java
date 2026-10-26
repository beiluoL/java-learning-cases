package com.example.csagent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 满意度评价。用户在问题被解决后可对本次会话打分（1-5）并留言。
 */
@Data
@Entity
@Table(name = "satisfactions", indexes = {
        @Index(name = "idx_sat_conv", columnList = "conversation_id")
})
public class Satisfaction {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    /** 评分 1-5 */
    @Column(nullable = false)
    private int score;

    /** 文字评价（可选） */
    @Column(length = 500)
    private String comment;

    /** 评价时会话的意图标签（用于统计） */
    @Column(length = 30)
    private String intent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
