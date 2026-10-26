package com.ecommerce.learn.security;

import lombok.Data;

/**
 * 当前登录用户上下文（一次请求内的身份载体）
 */
@Data
public class LoginUser {

    private Long userId;
    private String username;
    private String role;

    public LoginUser(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }
}
