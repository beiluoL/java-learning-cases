package com.example.mq.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 投递测试消息的结果 */
@Data
@AllArgsConstructor
public class PublishResultView {

    /** true = 至少路由到了一个队列；false = 到了交换机但没有匹配的绑定 */
    private boolean routed;

    private String note;
}
