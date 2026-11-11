package com.example.ai.common;

import org.slf4j.MDC;

import java.util.UUID;

/** 链路 ID 工具：写入 MDC，供日志、响应与账单共用。 */
public final class TraceContext {

    public static final String TRACE_ID = "traceId";

    private TraceContext() {
    }

    /** 生成不带连字符的短 UUID，便于日志检索。 */
    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    public static void put(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static String current() {
        String id = MDC.get(TRACE_ID);
        return id == null ? "" : id;
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
    }
}
