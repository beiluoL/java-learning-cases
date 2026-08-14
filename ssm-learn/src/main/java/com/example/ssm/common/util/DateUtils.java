package com.example.ssm.common.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日期工具类：基于 ThreadLocal 的 SimpleDateFormat，线程安全，避免重复 new 对象。
 */
public class DateUtils {

    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final ThreadLocal<SimpleDateFormat> SDF =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN));

    /** Date -> 字符串 */
    public static String format(Date date) {
        return date == null ? "" : SDF.get().format(date);
    }

    /** 字符串 -> Date（解析失败抛 ParseException） */
    public static Date parse(String source) throws ParseException {
        return source == null ? null : SDF.get().parse(source);
    }

    /** 取当前时间 */
    public static Date now() {
        return new Date();
    }
}
