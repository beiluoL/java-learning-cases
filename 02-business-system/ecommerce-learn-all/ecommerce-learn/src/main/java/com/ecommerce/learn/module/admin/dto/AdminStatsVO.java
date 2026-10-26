package com.ecommerce.learn.module.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 后台看板统计数据
 */
@Data
public class AdminStatsVO {

    private long userCount;
    private long productCount;
    private long orderCount;
    private long paidOrderCount;
    private BigDecimal totalSales;
    private long todayOrderCount;
    private long seckillActivityCount;
}
