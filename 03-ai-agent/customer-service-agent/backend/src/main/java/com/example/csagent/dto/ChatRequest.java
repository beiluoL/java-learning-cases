package com.example.csagent.dto;

import lombok.Data;

/** 客户端发起对话请求体。 */
@Data
public class ChatRequest {
    /** 已有会话 ID；为空则新建 */
    private String conversationId;
    /** 用户输入 */
    private String message;
    /** 用户主动要求转人工 */
    private boolean forceEscalate = false;
}
