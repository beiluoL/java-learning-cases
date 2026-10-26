package com.enterprise.erpcrm.common.exception;

import com.enterprise.erpcrm.common.result.RCode;
import java.util.Collection;
import java.util.Map;

/**
 * 断言工具：校验不通过即抛出 {@link BizException}，避免各业务方法散落大量 if-throw 样板代码。
 * <p>仅用于业务前置校验；参数级校验优先交由 {@code @Validated}/{@code @Valid} 完成。</p>
 *
 * @author erp-crm-system
 */
public final class AssertUtil {

    private AssertUtil() {
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BizException(RCode.BIZ_ERROR, message);
        }
    }

    public static void isTrue(boolean expression, RCode rCode, String message) {
        if (!expression) {
            throw new BizException(rCode, message);
        }
    }

    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new BizException(RCode.BIZ_ERROR, message);
        }
    }

    public static void notEmpty(Collection<?> coll, String message) {
        if (coll == null || coll.isEmpty()) {
            throw new BizException(RCode.BIZ_ERROR, message);
        }
    }

    public static void notEmpty(Map<?, ?> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new BizException(RCode.BIZ_ERROR, message);
        }
    }

    public static void notBlank(String str, String message) {
        if (str == null || str.isBlank()) {
            throw new BizException(RCode.BIZ_ERROR, message);
        }
    }

    /** 状态校验：不满足即失败（用于流程状态机等场景） */
    public static void state(boolean expression, RCode rCode, String message) {
        if (!expression) {
            throw new BizException(rCode, message);
        }
    }
}
