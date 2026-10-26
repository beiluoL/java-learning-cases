package com.finpay.security.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一条审计日志记录。由切面构造、交予 {@link AuditLogRecorder} 持久化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogRecord {

    /** 操作者标识（用户ID / 系统） */
    private String operator;
    /** 操作类型 */
    private String action;
    /** 资源类型 */
    private String resource;
    /** 业务主键 */
    private String bizId;
    /** 来源 IP */
    private String ip;
    /** 渠道（支付场景可标识 alipay/wechat/internal） */
    private String channel;
    /** 请求参数摘要（已脱敏） */
    private String params;
    /** 操作结果：SUCCESS / FAIL */
    private String result;
    /** 失败原因 */
    private String error;
    /** 发生时间 */
    private LocalDateTime occurTime;
}
