package com.example.csagent.dto;

import lombok.Data;

import java.util.List;

/** Agent 一次应答的完整结果（供 Controller 分片流式推送）。 */
@Data
public class AgentReply {
    private String content;
    private Intent intent;
    private double intentConfidence;
    private String matchedKeywords;
    private List<FaqMatch> faqs;
    private boolean escalated;
    private String escalateTicketId;
    private String escalateReason;
    /** 是否为寒暄/致谢类的兜底回复（不转人工） */
    private boolean greeting;
}
