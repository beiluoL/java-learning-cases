package com.ecommerce.learn.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 后台发货请求
 */
@Data
public class AdminShipDTO {

    @NotBlank(message = "物流公司不能为空")
    private String logisticsCompany;

    @NotBlank(message = "物流单号不能为空")
    private String logisticsNo;
}
