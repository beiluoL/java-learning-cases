package com.iot.bigdata.opinion.entity;

import com.iot.bigdata.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 舆情记录表。存储来自各渠道的舆情文本及其情感分析结果。
 *
 * <p>遵循阿里建表规约：逻辑外键（无跨表关联）、必备四字段、状态/话题/时间均建索引。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "opinion_record", indexes = {
        @Index(name = "idx_sentiment", columnList = "sentiment"),
        @Index(name = "idx_topic", columnList = "topic"),
        @Index(name = "idx_publish_time", columnList = "publish_time"),
        @Index(name = "idx_stat_date", columnList = "stat_date")
})
public class OpinionRecord extends BaseEntity {

    /** 来源渠道：微博 / 抖音 / 新闻 / 论坛 等。 */
    @Column(name = "source", length = 32)
    private String source;

    /** 作者。 */
    @Column(name = "author", length = 64)
    private String author;

    /** 文本内容。 */
    @Column(name = "content", length = 2000)
    private String content;

    /** 情感倾向：POSITIVE / NEGATIVE / NEUTRAL。 */
    @Column(name = "sentiment", length = 16)
    private String sentiment;

    /** 情感得分 [-1, 1]。 */
    @Column(name = "sentiment_score", precision = 6, scale = 4)
    private BigDecimal sentimentScore;

    /** 话题标签。 */
    @Column(name = "topic", length = 64)
    private String topic;

    /** 热度（浏览/互动量）。 */
    @Column(name = "hot")
    private Integer hot;

    /** 发布时间。 */
    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    /** 统计日期（用于按天趋势分组）。 */
    @Column(name = "stat_date")
    private LocalDate statDate;

    /** 原文链接。 */
    @Column(name = "url", length = 255)
    private String url;
}
