package com.ecommerce.learn.security;

/**
 * 鉴权相关常量
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    public static final String HEADER_NAME = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";
}
