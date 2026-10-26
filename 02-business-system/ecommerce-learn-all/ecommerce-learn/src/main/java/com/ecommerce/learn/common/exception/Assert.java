package com.ecommerce.learn.common.exception;

import java.util.function.Supplier;

/**
 * 断言工具：在 Service 层做前置校验，失败时抛出 {@link BizException}
 */
public final class Assert {

    private Assert() {
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BizException(message);
        }
    }

    public static void isTrue(boolean expression, CodeEnum codeEnum) {
        if (!expression) {
            throw new BizException(codeEnum);
        }
    }

    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new BizException(message);
        }
    }

    public static void notEmpty(String str, String message) {
        if (str == null || str.isBlank()) {
            throw new BizException(message);
        }
    }

    /** 提供异常 Supplier，便于复用异常实例 */
    public static void isTrue(boolean expression, Supplier<BizException> exceptionSupplier) {
        if (!expression) {
            throw exceptionSupplier.get();
        }
    }
}
