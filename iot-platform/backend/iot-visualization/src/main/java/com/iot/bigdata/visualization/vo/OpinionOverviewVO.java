package com.iot.bigdata.visualization.vo;

import com.iot.bigdata.opinion.vo.HotTopicVO;
import com.iot.bigdata.opinion.vo.OpinionStatVO;
import com.iot.bigdata.opinion.vo.OpinionTrendVO;

import lombok.Data;

import java.util.List;

/**
 * 舆情总览（统计 + 趋势 + 热点）。
 */
@Data
public class OpinionOverviewVO {

    /** 情感分布统计。 */
    private OpinionStatVO stat;

    /** 按天趋势。 */
    private List<OpinionTrendVO> trend;

    /** 热点话题。 */
    private List<HotTopicVO> hotTopics;
}
