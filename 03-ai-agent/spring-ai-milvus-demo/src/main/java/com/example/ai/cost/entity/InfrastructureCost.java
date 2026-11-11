package com.example.ai.cost.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 基础设施成本：区分直接计量(METERED)与分摊(ALLOCATED)与固定成本(FIXED)。 */
@Data
public class InfrastructureCost {
    /** 主键 */
    private Long id;
    /** 资源类型：SERVER / GPU / REDIS / MYSQL / MILVUS / OBJECT_STORAGE / NETWORK / MONITORING / LOCAL_MODEL */
    private String resourceType;
    /** 资源名称 */
    private String resourceName;
    /** 云厂商或 self-hosted */
    private String provider;
    /** 计费周期开始 */
    private LocalDate periodStart;
    /** 计费周期结束 */
    private LocalDate periodEnd;
    /** 周期总成本 */
    private BigDecimal amount;
    /** 货币 */
    private String currency;
    /** METERED 直接计量 / ALLOCATED 按量分摊 / FIXED 固定成本 */
    private String allocationMethod;
    /** 已分摊金额（不得超过 amount） */
    private BigDecimal allocatedAmount;
    /** 分摊依据的请求量（用于计算单位分摊成本） */
    private Long requestCount;
    /** 1 表示 DEMO 数据，非真实云账单 */
    private Integer demoFlag;
    /** 备注 */
    private String remark;
    /** 创建时间(UTC) */
    private LocalDateTime createdAt;
}
