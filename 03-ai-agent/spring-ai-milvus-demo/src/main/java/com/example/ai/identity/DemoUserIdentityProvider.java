package com.example.ai.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发/测试环境的固定 Demo 身份。仅用于单机演示与自动化测试，不是多用户生产权限系统。
 * 生产环境设置 ai.identity.mode=real 关闭本 Bean，并自行注册真实认证的 UserIdentityProvider 实现
 * （否则启动会因缺少该 Bean 失败，这是刻意的安全失败）。
 */
@Component
@ConditionalOnProperty(name = "ai.identity.mode", havingValue = "demo", matchIfMissing = true)
public class DemoUserIdentityProvider implements UserIdentityProvider {

    public static final String DEMO_USER = "demo-user";

    @Override
    public String currentUserId() {
        return DEMO_USER;
    }
}
