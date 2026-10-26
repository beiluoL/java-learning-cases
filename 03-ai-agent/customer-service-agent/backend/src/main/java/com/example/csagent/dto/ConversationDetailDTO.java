package com.example.csagent.dto;

import com.example.csagent.model.Conversation;
import com.example.csagent.model.EscalationTicket;
import com.example.csagent.model.Message;
import lombok.Data;

import java.util.List;

/** 会话详情：会话元信息 + 消息列表 + 关联工单。 */
@Data
public class ConversationDetailDTO {
    private Conversation conversation;
    private List<Message> messages;
    private EscalationTicket ticket;
}
