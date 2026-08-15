package com.enterprise.erpcrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志实体（sys_oper_log）。仅审计写入，无需逻辑删除/审计字段回填，故不继承 BaseEntity。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String title;
    private Integer businessType;
    private String method;
    private String requestMethod;
    private Long operUserId;
    private String operName;
    private String operDeptName;
    private String operUrl;
    private String operIp;
    private String operParam;
    private String jsonResult;
    private Integer status;
    private String errorMsg;
    private Long costTime;
    private LocalDateTime operTime;
    private LocalDateTime createTime;
}
