package com.iot.bigdata.opinion.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 舆情记录视图对象。
 */
@Data
public class OpinionVO {

    private Long id;
    private String source;
    private String author;
    private String content;
    private String sentiment;
    private BigDecimal sentimentScore;
    private String topic;
    private Integer hot;
    private LocalDateTime publishTime;
    private String url;
}
