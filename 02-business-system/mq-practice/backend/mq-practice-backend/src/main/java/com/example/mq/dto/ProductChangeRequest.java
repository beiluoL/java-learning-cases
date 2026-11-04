package com.example.mq.dto;

import com.example.mq.common.enums.ProductChangeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 商品变更请求：触发场景三的事件发布 */
@Data
public class ProductChangeRequest {

    @NotNull(message = "变更类型不能为空")
    private ProductChangeType changeType;

    /** 改价时必填 */
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    /** 改名时必填 */
    private String name;

    /** 操作人，仅用于消息头与日志 */
    private String operator = "console";
}
