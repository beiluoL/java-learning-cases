package com.finpay.common.util;

import java.net.InetAddress;

/**
 * 雪花算法分布式 ID 生成器。生成 64 位趋势递增 long 型 ID，用于订单号、流水号等。
 * 结构：1 位符号位 + 41 位时间戳差 + 10 位机器位 + 12 位序列号。
 */
public class SnowflakeId {

    private static final long EPOCH = 1700000000000L;
    private static final long WORKER_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER = ~(-1L << WORKER_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeId() {
        this(resolveWorkerId());
    }

    public SnowflakeId(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER) {
            throw new IllegalArgumentException("workerId 超出范围 0~" + MAX_WORKER);
        }
        this.workerId = workerId;
    }

    private static long resolveWorkerId() {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            long hash = host.hashCode() & MAX_WORKER;
            return hash;
        } catch (Exception e) {
            return 1L;
        }
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID");
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                ts = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }

    /** 生成支付订单号：前缀 + 时间戳 + 雪花ID */
    public String nextOrderNo(String prefix) {
        return prefix + nextId();
    }
}
