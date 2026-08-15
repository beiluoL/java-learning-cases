package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统菜单实体（sys_menu）。menuType：M 目录 / C 菜单 / F 按钮。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String menuName;
    private Long parentId;
    private Integer orderNum;
    private String path;
    private String component;
    private String queryParam;
    private String menuType;
    private String perms;
    private String icon;
    private Integer isFrame;
    private Integer isCache;
    private Integer visible;
    private Integer status;
    private String remark;
    private String createBy;
    private String updateBy;

    /** 非持久化：子菜单（树构建用） */
    @TableField(exist = false)
    private List<SysMenu> children;
}
