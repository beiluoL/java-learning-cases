package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户实体（sys_user）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String realName;
    private String nickName;
    private String avatar;
    private String email;
    private String phone;
    /** 性别：0 未知 1 男 2 女 */
    private Integer gender;
    /** 所属部门 id（逻辑外键 sys_dept.id） */
    private Long deptId;
    /** 所属岗位 id（逻辑外键 sys_post.id） */
    private Long postId;
    /** 用户类型：0 系统内置 1 普通用户 */
    private Integer userType;
    /** 状态：0 正常 1 停用 */
    private Integer status;
    private String loginIp;
    private LocalDateTime loginTime;
    private String remark;
    private String createBy;
    private String updateBy;

    /** 非持久化：角色标识集合（登录时组装） */
    @TableField(exist = false)
    private java.util.List<String> roleKeys;
    /** 非持久化：部门名称（列表展示用） */
    @TableField(exist = false)
    private String deptName;
}
