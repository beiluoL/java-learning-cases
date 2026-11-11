package com.example.ai.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额与 Redis 整数单位的换算。Lua 只能用整数运算保证精度，
 * 统一以 1e-8 USD 为 1 个单位（与 DECIMAL(20,8) 刻度一致）。
 * 单位值必须小于 2^53，Lua 的 number 才能精确表示整数，因此限制单笔预算上限。
 */
public final class MoneyScale {

    public static final int SCALE = 8;
    public static final BigDecimal UNIT = BigDecimal.valueOf(100_000_000L);
    public static final BigDecimal MAX_BUDGET = new BigDecimal("10000000");

    private MoneyScale() {
    }

    public static long toUnits(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.compareTo(MAX_BUDGET) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "金额超出预算控制上限 " + MAX_BUDGET + " USD");
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP).multiply(UNIT).longValueExact();
    }

    public static BigDecimal fromUnits(long units) {
        return BigDecimal.valueOf(units).divide(UNIT, SCALE, RoundingMode.HALF_UP);
    }

    /** 金额统一保留 8 位小数，四舍五入，禁止 float/double 参与计算。 */
    public static BigDecimal normalize(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
