package com.enterprise.erpcrm.common.util;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号 / 分布式 id 生成工具。
 * <p>
 * - {@link #genId()}            雪花算法全局唯一 id（用于非自增主键场景）；
 * - {@link #genOrderNo(String)} 人读业务单号：{prefix}{yyyyMMdd}{4 位当日流水}，如 PO202608150001。
 * </p>
 *
 * @author erp-crm-system
 */
public final class SnGenerator {

    private SnGenerator() {
    }

    /** 雪花算法实例（Hutool 内部以数据中心/机器位=0 构造，演示足够） */
    private static final cn.hutool.core.lang.Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    /** 当日流水号与日期标记（重置用） */
    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static volatile String seqDate = today();

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
    }

    /** 生成全局唯一长整型 id */
    public static long genId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 生成业务单号：前缀 + 日期 + 4 位流水。
     * 线程安全：跨天自动重置流水。
     */
    public static synchronized String genOrderNo(String prefix) {
        String now = today();
        if (!seqDate.equals(now)) {
            seqDate = now;
            SEQ.set(0);
        }
        int next = SEQ.incrementAndGet();
        return prefix + now + String.format("%04d", next);
    }

    /** 日期工具占位，避免未使用告警（保留供扩展） */
    public static String nowPattern() {
        return DateUtil.date().toString(DatePattern.NORM_DATETIME_PATTERN);
    }
}
