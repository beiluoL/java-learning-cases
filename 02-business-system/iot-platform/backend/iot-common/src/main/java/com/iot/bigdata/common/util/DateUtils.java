package com.iot.bigdata.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 日期工具类。全部基于 {@link DateTimeFormatter}（线程安全），不使用 {@code SimpleDateFormat}。
 */
public final class DateUtils {

    private DateUtils() {
    }

    /** 标准日期时间格式：yyyy-MM-dd HH:mm:ss。 */
    public static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 标准日期格式：yyyy-MM-dd。 */
    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String formatNow() {
        return LocalDateTime.now().format(DATETIME_FORMATTER);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FORMATTER);
    }

    public static LocalDateTime parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, DATETIME_FORMATTER);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMATTER);
    }

    /**
     * 按指定步长向前截取时间窗口起点（用于实时分析的时间分桶）。
     *
     * @param windowSeconds 窗口大小（秒）
     * @return 当前时间所在窗口的起点
     */
    public static LocalDateTime windowStart(int windowSeconds) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long truncated = (now.toEpochSecond(java.time.ZoneOffset.of("+8")) / windowSeconds) * windowSeconds;
        return LocalDateTime.ofEpochSecond(truncated, 0, java.time.ZoneOffset.of("+8"));
    }
}
