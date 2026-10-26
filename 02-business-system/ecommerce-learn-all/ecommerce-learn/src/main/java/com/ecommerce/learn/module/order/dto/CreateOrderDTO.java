package com.ecommerce.learn.module.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderDTO {

    /** 直接购买项（与 useCart 二选一） */
    @Valid
    private List<OrderItemDTO> items;

    /** 是否使用购物车中已勾选项 */
    private Boolean useCart = false;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货手机号不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    private String remark;
}
