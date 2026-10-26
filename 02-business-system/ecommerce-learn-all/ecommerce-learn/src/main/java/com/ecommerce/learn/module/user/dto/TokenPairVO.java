package com.ecommerce.learn.module.user.dto;

import lombok.Data;

/**
 * 登录成功返回的令牌对
 */
@Data
public class TokenPairVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    /** 访问令牌有效期（秒） */
    private long expiresIn;
    private UserVO user;
}
