package com.finpay.security.service;

import com.finpay.common.exception.BizException;
import com.finpay.common.result.ErrorCode;
import com.finpay.common.util.RSAUtil;
import com.finpay.security.key.KeyManager;
import org.springframework.stereotype.Service;

/**
 * 签名验签服务。封装「平台私钥签名」与「渠道公钥验签」两类操作。
 * 回调场景下，需用对应渠道的公钥校验回传报文的真实性，杜绝伪造回调。
 */
@Service
public class SignVerifyService {

    private final KeyManager keyManager;

    public SignVerifyService(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /** 使用平台私钥对明文签名（如给渠道的请求做商户签名） */
    public String signByPlatform(String content) {
        return RSAUtil.sign(content, keyManager.getPlatformPrivateKey());
    }

    /** 使用平台公钥验签（校验平台自身签发的数据） */
    public boolean verifyByPlatform(String content, String sign) {
        return RSAUtil.verify(content, sign, keyManager.getPlatformPublicKey());
    }

    /**
     * 使用渠道公钥校验回调报文签名。
     * @param channel    渠道标识 alipay / wechat
     * @param merchantNo 商户号
     * @param content    被签名原文
     * @param sign       渠道签名
     */
    public boolean verifyChannelCallback(String channel, String merchantNo, String content, String sign) {
        String pub = keyManager.getChannelPublicKey(channel, merchantNo);
        if (pub == null) {
            throw new BizException(ErrorCode.SIGN_VERIFY_FAIL, "未配置渠道公钥：" + channel + ":" + merchantNo);
        }
        return RSAUtil.verify(content, sign, pub);
    }
}
