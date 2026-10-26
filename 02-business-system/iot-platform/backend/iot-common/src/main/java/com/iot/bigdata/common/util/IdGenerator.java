package com.iot.bigdata.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 分布式 ID 生成工具。基于 Hutool 的雪花算法实现，保证单进程内唯一、趋势递增。
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    /** 数据中心 ID 与机器 ID 固定为 1（学习项目单实例即可；多实例部署时按机器分配）。 */
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return String.valueOf(nextId());
    }
}
