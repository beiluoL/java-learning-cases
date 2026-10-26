package com.finpay.common.util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Cipher;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 非对称加密工具（基于 JCE，无第三方依赖）。用于「签名/验签」与「敏感字段非对称加密」。
 * 典型用法：平台私钥签名、渠道公钥验签；通道公钥加密回调敏感字段、平台私钥解密。
 */
public final class RSAUtil {

    private RSAUtil() {}

    /** 生成 RSA 2048 密钥对，返回 [privateBase64(PKCS8), publicBase64(X509)] */
    public static String[] generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            KeyPair pair = gen.generateKeyPair();
            return new String[]{
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
            };
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }

    private static PrivateKey loadPrivateKey(String b64) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64));
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static PublicKey loadPublicKey(String b64) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(b64));
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /** 使用平台私钥对明文签名（SHA256withRSA），返回 Base64 */
    public static String sign(String data, String privateKeyBase64) {
        try {
            PrivateKey key = loadPrivateKey(privateKeyBase64);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("RSA 签名失败", e);
        }
    }

    /** 使用平台公钥验签（SHA256withRSA） */
    public static boolean verify(String data, String signBase64, String publicKeyBase64) {
        try {
            PublicKey key = loadPublicKey(publicKeyBase64);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            return false;
        }
    }

    /** 使用公钥加密（RSA/ECB/PKCS1Padding），返回 Base64 */
    public static String encrypt(String data, String publicKeyBase64) {
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, loadPublicKey(publicKeyBase64));
            return Base64.getEncoder().encodeToString(c.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败", e);
        }
    }

    /** 使用私钥解密，返回明文 */
    public static String decrypt(String cipherBase64, String privateKeyBase64) {
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.DECRYPT_MODE, loadPrivateKey(privateKeyBase64));
            return new String(c.doFinal(Base64.getDecoder().decode(cipherBase64)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 解密失败", e);
        }
    }
}
