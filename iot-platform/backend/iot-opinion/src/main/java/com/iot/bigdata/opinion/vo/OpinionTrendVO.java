package com.iot.bigdata.opinion.vo;

import lombok.Data;

/**
 * 舆情趋势视图对象（按天聚合）。
 */
@Data
public class OpinionTrendVO {

    /** 统计日期（yyyy-MM-dd）。 */
    private String date;

    /** 正向数。 */
    private long positive;

    /** 负向数。 */
    private long negative;

    /** 中性数。 */
    private long neutral;
}
