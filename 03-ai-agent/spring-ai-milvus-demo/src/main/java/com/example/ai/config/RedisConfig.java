package com.example.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/** Redis Lua 脚本集中加载，避免在业务代码里拼接脚本字符串。 */
@Configuration
public class RedisConfig {

    @Bean("budgetReserveScript")
    public DefaultRedisScript<List> budgetReserveScript() {
        return script("lua/budget_reserve.lua");
    }

    @Bean("budgetSettleScript")
    public DefaultRedisScript<List> budgetSettleScript() {
        return script("lua/budget_settle.lua");
    }

    @Bean("budgetReleaseScript")
    public DefaultRedisScript<List> budgetReleaseScript() {
        return script("lua/budget_release.lua");
    }

    @Bean("budgetCleanupScript")
    public DefaultRedisScript<List> budgetCleanupScript() {
        return script("lua/budget_cleanup.lua");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DefaultRedisScript<List> script(String location) {
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource(location));
        s.setResultType((Class) List.class);
        return s;
    }
}
