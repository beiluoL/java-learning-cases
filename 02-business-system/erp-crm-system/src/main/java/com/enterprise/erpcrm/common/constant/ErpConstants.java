package com.enterprise.erpcrm.common.constant;

/**
 * 系统级通用常量。集中管理，避免魔法值散落。
 *
 * @author erp-crm-system
 */
public final class ErpConstants {

    private ErpConstants() {
    }

    /** 系统名称 */
    public static final String APP_NAME = "erp-crm-system";

    /** 逻辑删除：未删除 / 已删除 */
    public static final int DELETED_NO = 0;
    public static final int DELETED_YES = 1;

    /** 状态：正常 / 停用（通用启用停用枚举值） */
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_DISABLE = 1;

    /** 是 / 否 */
    public static final int YES = 1;
    public static final int NO = 0;

    /** 超级管理员用户 id（种子数据固定为 1），用于跳过部分限制 */
    public static final Long SUPER_ADMIN_USER_ID = 1L;

    /** 缓存：超级管理员标识的权限集合（空集合，表示放行全部） */
    public static final String PERM_ALL = "*:*:*";

    /** 超级管理员角色 key（与 sys_role.role_key 一致） */
    public static final String SUPER_ADMIN_ROLE_KEY = "SUPER_ADMIN";

    /** 超级管理员权限通配符（角色含全部菜单权限时返回此值） */
    public static final String SUPER_ADMIN_PERM = "*:*:*";
}
