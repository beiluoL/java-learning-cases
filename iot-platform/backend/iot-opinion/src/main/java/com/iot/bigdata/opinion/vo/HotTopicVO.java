package com.iot.bigdata.opinion.vo;

import lombok.Data;

/**
 * 热点话题视图对象。
 */
@Data
public class HotTopicVO {

    /** 话题。 */
    private String topic;

    /** 提及次数。 */
    private long count;

    /** 累计热度。 */
    private long hot;
}
