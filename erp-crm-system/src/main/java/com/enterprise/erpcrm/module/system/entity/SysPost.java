package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统岗位实体（sys_post）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_post")
public class SysPost extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String postCode;
    private String postName;
    private Integer postSort;
    private Integer status;
    private String remark;
    private String createBy;
    private String updateBy;
}
