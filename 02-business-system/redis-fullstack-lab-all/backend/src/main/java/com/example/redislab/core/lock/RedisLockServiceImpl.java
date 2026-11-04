package com.example.redislab.core.lock;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 手写分布式锁（SET NX EX + Lua 解锁 + 看门狗续期）。
 *
 * <p>实现要点：
 * 1) 加锁用单条 `SET key uuid NX EX lease` —— 原子操作，避免「先 SETNX 再 EXPIRE」两步之间的进程崩溃导致死锁。
 * 2) value 用 UUID，解锁时用 Lua 比对 value，只有持有者才能删，杜绝「误删他人锁」。
 * 3) 看门狗：后台定时线程在锁过期前 1/3 周期续期，防止业务没跑完锁就过期（Redisson 也是这个思路）。</p>
 */
@Service("redisLockService")
public class RedisLockServiceImpl implements LockService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> unlockScript;
    private final ScheduledExecutorService watchdog = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "redis-lock-watchdog");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, WatchdogTask> held = new ConcurrentHashMap<>();

    public RedisLockServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        // 原子解锁脚本：if get(key)==token then del(key)
        this.unlockScript = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
    }

    @Override
    public String tryLockNow(String lockKey, long leaseSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, java.time.Duration.ofSeconds(leaseSeconds));
        if (Boolean.TRUE.equals(ok)) {
            startWatchdog(lockKey, token, leaseSeconds);
            return token;
        }
        return null;
    }

    @Override
    public String tryLock(String lockKey, long waitSeconds, long leaseSeconds) {
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String token = tryLockNow(lockKey, leaseSeconds);
            if (token != null) {
                return token;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean unlock(String lockKey, String token) {
        if (token == null) {
            return false;
        }
        stopWatchdog(lockKey);
        Long result = stringRedisTemplate.execute(unlockScript, Collections.singletonList(lockKey), token);
        return result != null && result == 1L;
    }

    /** 启动看门狗：每 lease/3 续一次期，仅当仍持有锁时才续 */
    private void startWatchdog(String lockKey, String token, long leaseSeconds) {
        long period = leaseSeconds * 1000L / 3;
        String renewScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";
        RedisScript<Long> renew = new DefaultRedisScript<>(renewScript, Long.class);
        ScheduledFuture<?> future = watchdog.scheduleAtFixedRate(() ->
                stringRedisTemplate.execute(renew, Collections.singletonList(lockKey), token, String.valueOf(leaseSeconds * 1000L)),
                period, period, TimeUnit.MILLISECONDS);
        held.put(lockKey, new WatchdogTask(token, future));
    }

    private void stopWatchdog(String lockKey) {
        WatchdogTask task = held.remove(lockKey);
        if (task != null) {
            task.future.cancel(true);
        }
    }

    private static class WatchdogTask {
        final String token;
        final ScheduledFuture<?> future;

        WatchdogTask(String token, ScheduledFuture<?> future) {
            this.token = token;
            this.future = future;
        }
    }
}
