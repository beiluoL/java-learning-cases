package com.example.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 秒杀压测结果：用来直观验证「100 并发抢 20 件库存，绝不超卖」 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StressTestResult {

    /** 并发请求数 */
    private int total;

    /** 抢购成功（进入排队）数 */
    private int success;

    /** 因售罄被拒绝数 */
    private int soldOut;

    /** 其它失败数（系统异常/商品下架等） */
    private int failed;

    /** 总耗时（毫秒） */
    private long durationMs;

    /** 平均单请求耗时（毫秒） */
    private long avgCostMs;

    /** 压测前可用库存 */
    private int stockBefore;

    /** 压测后可用库存 */
    private int stockAfter;

    /** 成功订单号（最多返回 50 个，避免响应过大） */
    private List<String> successOrderNos;

    /** 结论提示 */
    private String conclusion;
}
