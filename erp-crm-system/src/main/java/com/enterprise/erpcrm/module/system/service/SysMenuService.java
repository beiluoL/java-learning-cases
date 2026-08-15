package com.enterprise.erpcrm.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.enterprise.erpcrm.module.system.domain.vo.RouterVo;
import com.enterprise.erpcrm.module.system.entity.SysMenu;
import java.util.List;

/**
 * 系统菜单服务接口。
 *
 * @author erp-crm-system
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * 构建完整菜单树（用于角色授权勾选）。
     *
     * @return 一级菜单列表（含 children）
     */
    List<SysMenu> buildTree();

    /**
     * 依据用户权限生成前端路由树。
     *
     * @param userId 用户 id
     * @return Vue Router 路由列表
     */
    List<RouterVo> selectRouterByUserId(Long userId);

    /**
     * 查询用户可访问的菜单权限标识集合。
     *
     * @param userId 用户 id
     * @return perms 集合
     */
    List<String> selectPermsByUserId(Long userId);
}
