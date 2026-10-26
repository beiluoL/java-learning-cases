package com.finpay.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 对称加密工具。用于「敏感数据落库加密」与「报文传输加密」。
 * 默认 AES/ECB/PKCS5Padding（与多数支付渠道对齐）；如需更高强度可改 GCM 模式。
 * 密钥由安全模块 {@code KeyManager} 统一提供，禁止在业务代码硬编码。
 */
public final class AESUtil {

    private AESUtil() {}

    public static String encrypt(String plainText, String base64Key) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] enc = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public static String decrypt(String cipherText, String base64Key) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] dec = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    /** 生成 128 位随机密钥（Base64 编码），供密钥管理初始化使用 */
    public static String generateBase64Key() {
        byte[] raw = new byte[16];
        java.security.SecureRandom rng = new java.security.SecureRandom();
        rng.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
}
