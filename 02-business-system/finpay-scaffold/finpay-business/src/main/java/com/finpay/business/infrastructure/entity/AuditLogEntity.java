package com.finpay.business.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作审计日志表。配合 @AuditLog 切面，满足金融合规审计要求。 */
@Data
@TableName("t_audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String operator;
    private String action;
    private String resource;
    private String bizId;
    private String ip;
    private String channel;
    private String params;
    private String result;
    private String error;
    private LocalDateTime occurTime;
}
