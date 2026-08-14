package com.example.ssm.common.util;

/**
 * 雪花算法（Snowflake）分布式 ID 生成器。
 * 适用于需要全局唯一、且不想暴露数据库自增规律的场景（如订单号、日志追踪 ID）。
 * 用法：SnowflakeUtil idGen = new SnowflakeUtil(1, 1); long id = idGen.nextId();
 */
public class SnowflakeUtil {

    /** 起始时间戳（可自定义为项目上线时间，单位毫秒） */
    private static final long EPOCH = 1700000000000L;

    private static final long WORKER_BITS = 5L;
    private static final long DATACENTER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER = ~(-1L << WORKER_BITS);
    private static final long MAX_DATACENTER = ~(-1L << DATACENTER_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeUtil(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER || workerId < 0) {
            throw new IllegalArgumentException("workerId 必须在 0~" + MAX_WORKER + " 之间");
        }
        if (datacenterId > MAX_DATACENTER || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 必须在 0~" + MAX_DATACENTER + " 之间");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /** 生成下一个全局唯一 ID（线程安全） */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("系统时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long last) {
        long t = System.currentTimeMillis();
        while (t <= last) {
            t = System.currentTimeMillis();
        }
        return t;
    }
}
