package com.ecommerce.learn.module.pay.service;

import com.ecommerce.learn.module.order.entity.Order;
import com.ecommerce.learn.module.pay.dto.PayInfoVO;
import com.ecommerce.learn.module.pay.dto.PayNotifyDTO;

/**
 * 支付服务抽象：屏蔽具体支付渠道（支付宝/微信）差异
 * <p>真实环境：createPayment 调用渠道「统一下单」接口；handleNotify 校验签名与金额。</p>
 */
public interface PayService {

    /** 为订单创建支付（返回唤起支付所需参数） */
    PayInfoVO createPayment(Order order, String channel);

    /** 校验回调合法性（真实环境需验签 + 比对金额） */
    boolean verifyNotify(PayNotifyDTO dto);
}
