package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统部门实体（sys_dept）。ancestors 路径（如 "0,1,3"）用于「本部门及以下」数据权限计算。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long parentId;
    private String ancestors;
    private String deptName;
    private String deptCode;
    private Integer orderNum;
    private Long leaderUserId;
    private String leaderName;
    private String phone;
    private String email;
    private Integer status;
    private String remark;
    private String createBy;
    private String updateBy;

    /** 非持久化：子部门（树构建用） */
    @TableField(exist = false)
    private List<SysDept> children;
}
