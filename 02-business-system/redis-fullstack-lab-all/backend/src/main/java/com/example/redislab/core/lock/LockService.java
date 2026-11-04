package com.example.redislab.core.lock;

/**
 * 分布式锁抽象：用策略模式隔离「手写 Redis 锁」与「Redisson 锁」，
 * 上层业务（扣库存）只依赖此接口，可随时切换实现做对比教学。
 */
public interface LockService {

    /**
     * 阻塞式加锁：在 waitSeconds 内不断尝试，直到拿到锁或超过等待时间。
     * @return 锁持有凭证 token（用于安全解锁，防止误删他人的锁）；失败返回 null
     */
    String tryLock(String lockKey, long waitSeconds, long leaseSeconds);

    /**
     * 非阻塞加锁：只试一次，失败立即返回 null。
     */
    String tryLockNow(String lockKey, long leaseSeconds);

    /**
     * 解锁：仅当 token 与当前锁持有者匹配时才删除，避免误删别人刚拿到的锁。
     */
    boolean unlock(String lockKey, String token);
}
