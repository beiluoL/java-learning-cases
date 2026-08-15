package com.enterprise.erpcrm.framework.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码工具：基于 BCrypt 哈希，注册/改密时加密、登录时比对。
 * <p>BCrypt 自带随机盐与自适应代价，明文不落库；演示种子密码均为 {@code 123456} 的哈希。</p>
 *
 * @author erp-crm-system
 */
public final class PasswordUtils {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtils() {
    }

    /**
     * 对明文密码进行 BCrypt 加密。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希串
     */
    public static String encode(CharSequence rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 比对明文与已加密密码是否匹配。
     *
     * @param rawPassword 明文密码
     * @param encodedPassword BCrypt 哈希串
     * @return 匹配返回 true
     */
    public static boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 暴露底层 {@link PasswordEncoder}，便于注入到 Spring 容器复用同一实例。
     *
     * @return BCrypt 编码器单例
     */
    public static PasswordEncoder encoder() {
        return ENCODER;
    }
}
