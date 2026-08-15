package com.iot.bigdata.visualization.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 总览看板数据（多模块聚合结果）。
 */
@Data
public class DashboardOverviewVO {

    /** 设备总数。 */
    private long deviceTotal;

    /** 在线设备数。 */
    private long deviceOnline;

    /** 离线设备数。 */
    private long deviceOffline;

    /** 近 24 小时数据点总数。 */
    private long dataPointCount24h;

    /** 当前告警数。 */
    private long alertCount;

    /** 舆情总数。 */
    private long opinionTotal;

    /** 情感指数 [-1,1]。 */
    private double sentimentIndex;

    /** 数据生成时间。 */
    private LocalDateTime time;
}
