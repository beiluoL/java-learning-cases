package com.enterprise.erpcrm.common.constant;

/**
 * 缓存 Key 常量。统一前缀，便于排查与后续迁移至 Redis。
 *
 * @author erp-crm-system
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    /** 用户权限集合：cache:perm:{userId} -> Set<String> */
    public static final String CACHE_USER_PERM = "cache:perm:";
    /** 用户角色集合：cache:role:{userId} -> Set<String> */
    public static final String CACHE_USER_ROLE = "cache:role:";
    /** 角色权限集合：cache:role:perm:{roleId} -> Set<String> */
    public static final String CACHE_ROLE_PERM = "cache:role:perm:";
    /** 菜单树（按角色）：cache:menu:tree:{userId} -> List<MenuTreeVo> */
    public static final String CACHE_MENU_TREE = "cache:menu:tree:";
    /** 字典数据：cache:dict:{dictType} -> List<DictDataVo> */
    public static final String CACHE_DICT = "cache:dict:";
    /** 部门下拉树：cache:dept:tree -> List<DeptTreeVo> */
    public static final String CACHE_DEPT_TREE = "cache:dept:tree";

    /** 默认缓存过期（秒），与 application.yml caffeine spec 对齐 */
    public static final long CACHE_TTL_SECONDS = 1800L;
}
