package com.example.csagent.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体。一条会话包含多条消息（用户/助手/系统）。
 * role: USER / ASSISTANT / SYSTEM
 */
@Data
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_msg_conv", columnList = "conversation_id"),
        @Index(name = "idx_msg_seq", columnList = "conversation_id,seq")
})
public class Message {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MessageRole role;

    @Lob
    @Column(nullable = false)
    private String content;

    /** 该消息关联识别出的意图（可为空） */
    @Column(length = 30)
    private String intent;

    /** 命中的 FAQ id 列表，逗号分隔 */
    @Column(length = 500)
    private String faqIds;

    /** 该消息是否为转人工动作产生 */
    @Column(nullable = false)
    private boolean escalated = false;

    /** 同一会话内顺序序号，用于排序与流式续写 */
    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
