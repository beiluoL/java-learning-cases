package com.example.mq.dto;

import lombok.Builder;
import lombok.Data;

/** 商品变更结果：带上事件 messageId，方便前端提示"已发布，请在监控页观察两个消费者的处理时差" */
@Data
@Builder
public class ProductChangeResult {

    private ProductView product;

    private String changeType;

    private Integer version;

    private String messageId;

    private String message;
}
