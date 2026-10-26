package com.enterprise.erpcrm.framework.security;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户上下文模型：由 JWT 解析后放入 {@link SecurityHolder}（ThreadLocal）。
 * <p>Controller / Service / AOP 切面统一从此对象获取当前操作者身份与权限。</p>
 *
 * @author erp-crm-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String realName;
    /** 所属部门 id（数据权限计算用） */
    private Long deptId;
    /** 角色标识集合，如 [SUPER_ADMIN, SALES_MANAGER] */
    private List<String> roles;
    /** 功能权限标识集合，如 [system:user:list] */
    private List<String> perms;
    /** 是否超级管理员（角色包含 super-admin-role 标识） */
    private boolean superAdmin;
    /** 数据权限范围（1全部/2自定义部门/3本部门/4本部门及以下/5仅本人），取角色中最宽松一档 */
    private Integer dataScope;
}
