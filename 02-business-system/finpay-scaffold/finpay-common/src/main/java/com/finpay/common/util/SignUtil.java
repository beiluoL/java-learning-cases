package com.finpay.common.util;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 签名 / 验签工具。支付场景最常见的两类：
 * 1) MD5/HmacSHA256 对「key=value 按字典序拼接 + 密钥」做摘要（支付宝旧版、微信 V2 风格）。
 * 2) RSA 对摘要做非对称签名（见 {@link RSAUtil}）。
 * 本工具提供通用「拼接+摘要」能力，具体算法由渠道决定。
 */
public final class SignUtil {

    private SignUtil() {}

    /**
     * 将所有参数（排除 sign 字段与空值）按 key 字典序排序后，拼接为 a=b&c=d 形式。
     */
    public static String buildSortedQuery(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if ("sign".equals(k) || v == null || v.isEmpty()) {
                continue;
            }
            parts.add(k + "=" + v);
        }
        return String.join("&", parts);
    }

    /** HmacSHA256 摘要，返回十六进制大写 */
    public static String hmacSha256(String content, String key) {
        HMac mac = new HMac(HmacAlgorithm.HmacSHA256, key.getBytes(StandardCharsets.UTF_8));
        return mac.digestHex(content).toUpperCase();
    }

    /** MD5 摘要，返回大写十六进制 */
    public static String md5(String content) {
        return cn.hutool.crypto.digest.DigestUtil.md5Hex(content).toUpperCase();
    }
}
