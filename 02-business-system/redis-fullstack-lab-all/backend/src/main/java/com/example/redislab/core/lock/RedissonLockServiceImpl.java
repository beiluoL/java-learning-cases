package com.example.redislab.core.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁（生产首选）。
 *
 * <p>相比手写锁，Redisson 直接解决了三个痛点：
 * 1) 自动看门狗续期：lease 不传时默认 30s，并在业务未结束前自动续（手写版要自己写）。
 * 2) 可重入：同一线程可多次加锁，计数 +1，解锁需对应次数。
 * 3) 高可用：支持 RedLock（多 master 加锁），单机 Redis 宕机也不易死锁。</p>
 */
@Service("redissonLockService")
public class RedissonLockServiceImpl implements LockService {

    private final RedissonClient redissonClient;

    public RedissonLockServiceImpl(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public String tryLockNow(String lockKey, long leaseSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean ok = lock.tryLock(0, leaseSeconds, TimeUnit.SECONDS);
            return ok ? lockKey : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public String tryLock(String lockKey, long waitSeconds, long leaseSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean ok = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            return ok ? lockKey : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public boolean unlock(String lockKey, String token) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                return true;
            }
        } catch (IllegalMonitorStateException ignored) {
            // 当前线程并未持有该锁，忽略
        }
        return false;
    }
}
