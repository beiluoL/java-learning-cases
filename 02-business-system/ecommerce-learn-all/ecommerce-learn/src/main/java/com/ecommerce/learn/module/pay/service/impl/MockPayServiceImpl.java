package com.ecommerce.learn.module.pay.service.impl;

import com.ecommerce.learn.module.order.entity.Order;
import com.ecommerce.learn.module.pay.dto.PayInfoVO;
import com.ecommerce.learn.module.pay.dto.PayNotifyDTO;
import com.ecommerce.learn.module.pay.service.PayService;
import org.springframework.stereotype.Service;

/**
 * 模拟支付实现（学习用）：不接入真实渠道，返回可直接用于演示的支付参数。
 * 真实接入时新增 {@code AlipayPayServiceImpl} / {@code WechatPayServiceImpl} 即可，
 * 由 {@link com.ecommerce.learn.module.pay.service.PayService} 统一抽象。
 */
@Service
public class MockPayServiceImpl implements PayService {

    @Override
    public PayInfoVO createPayment(Order order, String channel) {
        PayInfoVO vo = new PayInfoVO();
        vo.setChannel(channel);
        vo.setOrderNo(order.getOrderNo());
        String prepay = "PREPAY_" + order.getOrderNo() + "_" + System.nanoTime();
        vo.setPrepayTradeNo(prepay);
        if ("alipay".equalsIgnoreCase(channel)) {
            vo.setPayUrl("https://mock.alipay.com/gateway?tradeNo=" + prepay);
            vo.setQrContent("alipay://mock?tradeNo=" + prepay);
        } else {
            vo.setPayUrl("weixin://wxpay/mock?tradeNo=" + prepay);
            vo.setQrContent("weixin://wxpay/mock?tradeNo=" + prepay);
        }
        return vo;
    }

    @Override
    public boolean verifyNotify(PayNotifyDTO dto) {
        // 真实环境：用平台公钥验签，并用 dto.getAmount() 与本地订单金额比对，防篡改
        return dto != null && dto.isSuccess() && dto.getOrderNo() != null;
    }
}
