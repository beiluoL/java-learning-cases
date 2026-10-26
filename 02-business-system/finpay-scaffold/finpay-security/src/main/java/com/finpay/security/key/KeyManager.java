package com.finpay.security.key;

import com.finpay.common.util.AESUtil;
import com.finpay.common.util.RSAUtil;
import com.finpay.security.config.CryptoProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥管理器（支付安全基石）。
 * 职责：
 * 1. 持有平台级 AES / RSA 密钥（来自配置中心 / KMS）。
 * 2. 支持按「渠道 + 商户」维度缓存对应渠道公钥（用于回调验签）。
 * 3. 提供统一取密钥入口，业务代码不直接接触原始密钥字符串。
 *
 * <p>生产建议：将密钥存储于 KMS，本类仅做内存缓存与解密加载；切勿打印密钥全文。</p>
 */
@Component
public class KeyManager {

    private final CryptoProperties props;
    private final Map<String, String> channelPublicKeyCache = new ConcurrentHashMap<>();

    public KeyManager(CryptoProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        if (props.getAesKey() == null) {
            // 本地未配置时自动生成一套测试密钥，仅用于开发联调
            props.setAesKey(AESUtil.generateBase64Key());
            String[] rsa = RSAUtil.generateKeyPair();
            props.setRsaPrivateKey(rsa[0]);
            props.setRsaPublicKey(rsa[1]);
        }
    }

    public String getAesKey() { return props.getAesKey(); }
    public String getPlatformPrivateKey() { return props.getRsaPrivateKey(); }
    public String getPlatformPublicKey() { return props.getRsaPublicKey(); }

    /**
     * 获取某支付渠道（如 alipay / wechat）的公钥，用于回调报文验签。
     * key 形如 "alipay:default" 或 "wechat:merchantNo"。
     */
    public String getChannelPublicKey(String channel, String merchantNo) {
        return channelPublicKeyCache.get(channel + ":" + merchantNo);
    }

    public void registerChannelPublicKey(String channel, String merchantNo, String publicKey) {
        channelPublicKeyCache.put(channel + ":" + merchantNo, publicKey);
    }
}
