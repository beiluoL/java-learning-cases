package com.example.mq.common.enums;

import lombok.Getter;

/** 商品变更类型，决定下游消费者如何处理（写缓存 / 删缓存 / 索引下线） */
@Getter
public enum ProductChangeType {

    /** 上架：写缓存 + 索引置为可检索 */
    ONLINE("上架"),

    /** 下架：删缓存 + 索引标记不可检索 */
    OFFLINE("下架"),

    /** 改价：刷新缓存 + 更新索引文档 */
    PRICE_CHANGED("改价"),

    /** 基础信息更新：刷新缓存 + 更新索引文档 */
    INFO_UPDATED("信息更新");

    private final String description;

    ProductChangeType(String description) {
        this.description = description;
    }
}
