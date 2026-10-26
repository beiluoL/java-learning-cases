package com.example.csagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 流式事件。前端按 type 处理：
 * init / intent / faq / text / escalate / done / error
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {
    private String type;
    private Object data;

    public static ChatEvent of(String type, Object data) {
        return new ChatEvent(type, data);
    }
}
