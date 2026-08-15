package com.iot.bigdata.opinion.vo;

import lombok.Data;

/**
 * 舆情统计视图对象（情感分布）。
 */
@Data
public class OpinionStatVO {

    /** 总数。 */
    private long total;

    /** 正向数。 */
    private long positive;

    /** 负向数。 */
    private long negative;

    /** 中性数。 */
    private long neutral;

    /** 负向占比（0~1）。 */
    private double negativeRatio;

    /** 情感指数（正向占比 - 负向占比，区间 [-1,1]）。 */
    private double sentimentIndex;
}
