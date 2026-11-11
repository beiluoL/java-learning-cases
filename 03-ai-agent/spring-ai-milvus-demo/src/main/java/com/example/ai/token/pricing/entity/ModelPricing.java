package com.example.ai.token.pricing.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模型价格及历史版本，金额一律 BigDecimal，禁止 float/double。 */
@Data
public class ModelPricing {
    private Long id;
    private String provider;
    private String modelName;
    private String modelType;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private BigDecimal cachedInputPrice;
    private String currency;
    private String version;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer enabled;
    private Integer demoFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
