package com.example.csagent.dto;

import lombok.Data;

/** 满意度评价请求。 */
@Data
public class SatisfactionRequest {
    private String conversationId;
    /** 1-5 */
    private int score;
    private String comment;
}
