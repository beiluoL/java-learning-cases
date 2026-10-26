package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典类型实体（sys_dict_type）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String dictName;
    private String dictType;
    private Integer status;
    private String remark;
    private String createBy;
    private String updateBy;

    /** 非持久化：关联字典项数量（列表展示用） */
    @TableField(exist = false)
    private Integer dictDataCount;
    /** 非持久化：字典项列表 */
    @TableField(exist = false)
    private List<SysDictData> dictDataList;
}
