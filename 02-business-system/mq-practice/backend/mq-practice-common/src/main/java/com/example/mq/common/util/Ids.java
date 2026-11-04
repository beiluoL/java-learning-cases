package com.example.mq.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量 ID 工具：订单号、消息 ID、TraceId。
 * <p>
 * 订单号格式：{@code NO + yyyyMMddHHmmss + 4 位循环序列 + 3 位随机}（共 2+14+4+3 = 23 位）。
 * 生产环境若要求全局有序、无冲突，应换成雪花算法 / 号段模式；这里保持零依赖、肉眼可读。
 */
public final class Ids {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static final int SEQ_MOD = 10000;

    private Ids() {
    }

    /** 生成订单号，如 NO202611042130150001876 */
    public static String orderNo() {
        int seq = Math.abs(SEQ.getAndIncrement() % SEQ_MOD);
        int rnd = ThreadLocalRandom.current().nextInt(1000);
        return "NO" + LocalDateTime.now().format(TS)
                + String.format("%04d", seq)
                + String.format("%03d", rnd);
    }

    /** 32 位无横线 UUID，用作 messageId */
    public static String messageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 16 位 TraceId */
    public static String traceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 从消息 ID 派生一个稳定的短串，用于日志后缀 */
    public static String shortId(String messageId) {
        if (messageId == null || messageId.length() <= 8) {
            return String.valueOf(messageId);
        }
        return messageId.substring(0, 8);
    }
}
