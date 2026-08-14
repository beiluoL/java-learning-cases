package com.example.csagent.model;

/** 会话状态枚举。 */
public enum ConversationStatus {
    ACTIVE,     // 进行中（Agent 自动处理）
    ESCALATED,  // 已转人工
    RESOLVED,   // 已解决（用户评价）
    CLOSED      // 已关闭
}
