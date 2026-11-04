package com.example.mq.common.enums;

import lombok.Getter;

/** 商品上下架状态，与数据库 tinyint 对应 */
@Getter
public enum ProductStatus {

    OFFLINE(0, "已下架"),
    ONLINE(1, "已上架");

    private final int code;
    private final String description;

    ProductStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ProductStatus of(Integer code) {
        if (code == null) {
            return OFFLINE;
        }
        return code == 1 ? ONLINE : OFFLINE;
    }
}
