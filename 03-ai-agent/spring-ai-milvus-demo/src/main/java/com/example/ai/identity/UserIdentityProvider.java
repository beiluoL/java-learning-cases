package com.example.ai.identity;

/**
 * 统一用户身份入口。业务层只依赖本接口，不信任前端传入的 userId。
 * 生产环境必须提供真实认证实现（Spring Security / JWT / 网关注入），替换 Demo 实现即可。
 */
public interface UserIdentityProvider {

    String currentUserId();
}
