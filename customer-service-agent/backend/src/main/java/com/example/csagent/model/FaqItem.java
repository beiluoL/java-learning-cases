package com.example.csagent.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * FAQ 知识库条目。用于「知识库检索」能力。
 * category 与意图对齐：REFUND / ORDER_QUERY / TECH_SUPPORT / GENERAL
 */
@Data
@Entity
@Table(name = "faq_items", indexes = {
        @Index(name = "idx_faq_category", columnList = "category")
})
public class FaqItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 分类（意图） */
    @Column(nullable = false, length = 30)
    private String category;

    /** 标准问题 */
    @Column(nullable = false, length = 300)
    private String question;

    /** 答案（支持多行） */
    @Lob
    @Column(nullable = false)
    private String answer;

    /** 检索关键词，逗号分隔，用于提升召回 */
    @Column(length = 500)
    private String keywords;

    /** 排序权重，越大越靠前 */
    @Column(nullable = false)
    private int weight = 1;
}
