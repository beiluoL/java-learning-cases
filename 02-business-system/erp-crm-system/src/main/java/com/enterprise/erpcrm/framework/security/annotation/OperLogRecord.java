package com.enterprise.erpcrm.framework.security.annotation;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 操作日志记录载体：由 {@code OperLogAspect} 组装，交由 system 模块的 {@code OperLogSink} 持久化。
 *
 * @author erp-crm-system
 */
@Data
@Builder
public class OperLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long operId;
    private String title;
    private Integer businessType;
    private String method;
    private String requestMethod;
    private String operName;
    private Long operUserId;
    private String operIp;
    private String operUrl;
    private String operParam;
    private Integer status;
    private String errorMsg;
    private LocalDateTime operTime;
    private Long costTime;
}
