package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典数据实体（sys_dict_data）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer dictSort;
    private String listClass;
    private String isDefault;
    private Integer status;
    private String remark;
    private String createBy;
    private String updateBy;
}
