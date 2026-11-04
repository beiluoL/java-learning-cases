package com.example.redislab.controller;

import com.example.redislab.common.R;
import com.example.redislab.common.StatService;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 观测接口：让前端「肉眼」看到 Redis 里的 key/值，以及各场景的命中/拦截计数。
 * 教学用，生产环境应加权限控制或移除。
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StatService statService;

    public DebugController(StringRedisTemplate stringRedisTemplate,
                           RedisTemplate<String, Object> redisTemplate,
                           StatService statService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.statService = statService;
    }

    /** 列出 key（调试接口，生产请改用 SCAN 避免 KEYS 阻塞） */
    @GetMapping("/keys")
    public R<Set<String>> keys(@RequestParam(defaultValue = "*") String pattern) {
        Set<String> result = stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> set = new HashSet<>();
            for (byte[] b : connection.keys(pattern.getBytes(StandardCharsets.UTF_8))) {
                set.add(new String(b, StandardCharsets.UTF_8));
            }
            return set;
        });
        return R.ok(result);
    }

    /** 按 key 获取值（自动识别类型：string/hash/zset/list/set） */
    @GetMapping("/get")
    public R<Map<String, Object>> get(@RequestParam String key) {
        DataType type = stringRedisTemplate.type(key);
        Object value = null;
        if (DataType.STRING.equals(type)) {
            value = stringRedisTemplate.opsForValue().get(key);
        } else if (DataType.HASH.equals(type)) {
            value = stringRedisTemplate.opsForHash().entries(key);
        } else if (DataType.ZSET.equals(type)) {
            value = stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        } else if (DataType.LIST.equals(type)) {
            value = stringRedisTemplate.opsForList().range(key, 0, -1);
        } else if (DataType.SET.equals(type)) {
            value = stringRedisTemplate.opsForSet().members(key);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("type", type == null ? "none" : type.name());
        map.put("value", value);
        return R.ok(map);
    }

    /** 各场景统计计数快照 */
    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(statService.snapshot());
    }

    /** 重置计数 */
    @PostMapping("/reset")
    public R<Void> reset() {
        statService.reset();
        return R.ok();
    }
}
