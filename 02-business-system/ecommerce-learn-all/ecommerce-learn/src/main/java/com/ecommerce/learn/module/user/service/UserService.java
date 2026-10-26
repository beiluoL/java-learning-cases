package com.ecommerce.learn.module.user.service;

import com.ecommerce.learn.module.user.dto.*;

public interface UserService {

    /** 注册新用户（默认角色 USER） */
    void register(RegisterDTO dto);

    /** 账号密码登录，返回令牌对 */
    TokenPairVO login(LoginDTO dto);

    /** 用刷新令牌换取新的访问令牌 */
    TokenPairVO refresh(String refreshToken);

    /** 获取当前用户资料 */
    UserVO profile(Long userId);

    /** 更新个人资料 */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);
}
