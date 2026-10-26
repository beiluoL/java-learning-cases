package com.ecommerce.learn.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器：时间有序 + 随机位，避免主键暴露与并发冲突
 * 格式：yyyyMMddHHmmss + 4位随机 + 2位自增(进程内)
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static int sequence = 0;

    private OrderNoGenerator() {
    }

    public static synchronized String next() {
        String time = LocalDateTime.now().format(FMT);
        int seq = (sequence++ % 10000);
        int rand = (int) (Math.random() * 9000) + 1000;
        return time + seq + rand;
    }
}
