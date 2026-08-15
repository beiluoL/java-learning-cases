package com.enterprise.erpcrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.system.entity.SysMenu;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统菜单 Mapper。
 *
 * @author erp-crm-system
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 用户菜单树：取该用户角色授权的菜单（仅目录/菜单，按树序排列） */
    @Select("SELECT DISTINCT m.* FROM sys_menu m "
            + "JOIN sys_role_menu rm ON m.id = rm.menu_id "
            + "JOIN sys_user_role ur ON rm.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND m.menu_type IN ('M','C') AND m.status = 0 AND m.visible = 0 "
            + "ORDER BY m.parent_id, m.order_num")
    List<SysMenu> selectMenuTreeByUserId(@Param("userId") Long userId);

    /** 用户功能权限标识集合（perms 非空） */
    @Select("SELECT DISTINCT m.perms FROM sys_menu m "
            + "JOIN sys_role_menu rm ON m.id = rm.menu_id "
            + "JOIN sys_user_role ur ON rm.role_id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND m.perms IS NOT NULL AND m.perms <> ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
