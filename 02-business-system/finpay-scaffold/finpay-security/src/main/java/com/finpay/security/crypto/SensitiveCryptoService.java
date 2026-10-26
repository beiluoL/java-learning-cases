package com.finpay.security.crypto;

import com.finpay.common.util.AESUtil;
import com.finpay.security.key.KeyManager;
import org.springframework.stereotype.Service;

/**
 * 敏感数据加解密服务。数据库落库前对「证件号 / 银行卡号 / 手机号」等字段加密，
 * 读取时解密。配合 {@code @SensitiveField} 与 JPA 转换器使用，亦可直接调用本服务。
 */
@Service
public class SensitiveCryptoService {

    private final KeyManager keyManager;

    public SensitiveCryptoService(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        return AESUtil.encrypt(plain, keyManager.getAesKey());
    }

    public String decrypt(String cipher) {
        if (cipher == null) return null;
        return AESUtil.decrypt(cipher, keyManager.getAesKey());
    }

    /** 仅保留前 3 后 4 位的脱敏展示，如 6222********1234 */
    public static String mask(String plain, int head, int tail) {
        if (plain == null || plain.length() <= head + tail) return plain;
        return plain.substring(0, head)
                + "*".repeat(plain.length() - head - tail)
                + plain.substring(plain.length() - tail);
    }
}
