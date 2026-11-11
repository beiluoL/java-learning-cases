package com.example.ai.pricing.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模型价格及历史版本，金额一律 BigDecimal，禁止 float/double。 */
@Data
public class ModelPricing {
    /** 主键 */
    private Long id;
    /** 模型供应商，如 openai / deepseek / mock / local */
    private String provider;
    /** 模型名称（以供应商返回值为准） */
    private String modelName;
    /** 模型类型：CHAT / EMBEDDING / RERANK */
    private String modelType;
    /** 每百万普通输入 Token 单价 */
    private BigDecimal inputPrice;
    /** 每百万输出 Token 单价 */
    private BigDecimal outputPrice;
    /** 每百万缓存命中输入 Token 单价 */
    private BigDecimal cachedInputPrice;
    /** 结算货币，初版统一 USD */
    private String currency;
    /** 价格版本，账单保存快照 */
    private String version;
    /** 生效时间(UTC) */
    private LocalDateTime effectiveFrom;
    /** 失效时间(UTC)，NULL 表示长期有效 */
    private LocalDateTime effectiveTo;
    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;
    /** 是否示例价格：1 表示 DEMO 报价，非供应商真实价格 */
    private Integer demoFlag;
    /** 创建时间(UTC) */
    private LocalDateTime createdAt;
    /** 更新时间(UTC) */
    private LocalDateTime updatedAt;
}
