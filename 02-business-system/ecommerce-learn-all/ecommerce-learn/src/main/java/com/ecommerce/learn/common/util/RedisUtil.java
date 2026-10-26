package com.ecommerce.learn.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类：封装常用操作，支持对象 JSON 序列化缓存
 * <p>
 * 设计说明：
 * - 字符串 / 计数类使用 {@link StringRedisTemplate}（轻量、可读）
 * - 对象缓存使用 {@code objectRedisTemplate}（JSON 序列化）
 * - 分布式锁使用 {@link #setIfAbsent(String, String, long)}（SET NX EX 语义）
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;
    private final ObjectMapper redisObjectMapper;

    /* ----------------------- 字符串 ----------------------- */

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /* ----------------------- 对象（JSON） ----------------------- */

    public void setObject(String key, Object value, long ttlSeconds) {
        objectRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public void setObject(String key, Object value) {
        objectRedisTemplate.opsForValue().set(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> clazz) {
        Object obj = objectRedisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }
        if (clazz.isInstance(obj)) {
            return (T) obj;
        }
        return redisObjectMapper.convertValue(obj, clazz);
    }

    public <T> T getObject(String key, TypeReference<T> typeReference) {
        Object obj = objectRedisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }
        return redisObjectMapper.convertValue(obj, typeReference);
    }

    /* ----------------------- 通用 ----------------------- */

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public boolean expire(String key, long ttlSeconds) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS));
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    public long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Long count = stringRedisTemplate.delete(keys);
        return count == null ? 0L : count;
    }

    /* ----------------------- 计数 ----------------------- */

    public long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    public long decrement(String key, long delta) {
        return stringRedisTemplate.opsForValue().decrement(key, delta);
    }

    /* ----------------------- 分布式锁（SET NX EX） ----------------------- */

    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS));
    }

    /* ----------------------- Hash ----------------------- */

    public void putHash(String key, String hashKey, Object value) {
        objectRedisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object getHash(String key, String hashKey) {
        return objectRedisTemplate.opsForHash().get(key, hashKey);
    }

    public Map<Object, Object> entriesHash(String key) {
        return objectRedisTemplate.opsForHash().entries(key);
    }

    /* ----------------------- Set ----------------------- */

    public Long addSet(String key, Object... values) {
        return objectRedisTemplate.opsForSet().add(key, values);
    }

    public Set<Object> membersSet(String key) {
        return objectRedisTemplate.opsForSet().members(key);
    }

    public boolean isMemberSet(String key, Object value) {
        return Boolean.TRUE.equals(objectRedisTemplate.opsForSet().isMember(key, value));
    }

    /* ----------------------- List ----------------------- */

    public Long pushList(String key, Object value) {
        return objectRedisTemplate.opsForList().rightPush(key, value);
    }

    public List<Object> rangeList(String key, long start, long end) {
        return objectRedisTemplate.opsForList().range(key, start, end);
    }

    /* ----------------------- 过期工具 ----------------------- */

    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /* ----------------------- Lua 脚本 ----------------------- */

    /** 执行 Lua 脚本（用于秒杀等需要原子性的复合操作） */
    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        return stringRedisTemplate.execute(script, keys, args);
    }
}
