package com.example.ai.common;

/** 业务错误码，与 docs/api/AI_COST_API.md 保持一致。 */
public final class ErrorCode {
    public static final int OK = 0;
    public static final int PARAM_INVALID = 40001;
    public static final int BUDGET_REJECTED = 40301;
    public static final int PROTECTION_BLOCKED = 40302;
    public static final int NOT_FOUND = 40401;
    public static final int DUPLICATE = 40901;
    public static final int RATE_LIMITED = 42901;
    public static final int SYSTEM = 50001;
    public static final int REDIS_UNAVAILABLE = 50301;

    private ErrorCode() {
    }
}
