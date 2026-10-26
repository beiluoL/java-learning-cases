package com.enterprise.erpcrm.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.enterprise.erpcrm.framework.security.annotation.DataScope;
import com.enterprise.erpcrm.module.system.domain.query.SysUserQuery;
import com.enterprise.erpcrm.module.system.entity.SysUser;

/**
 * 系统用户服务接口。
 *
 * @author erp-crm-system
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名查询（含部门名称），用于登录与详情。
     *
     * @param username 用户名
     * @return 用户实体或 null
     */
    SysUser selectByUsername(String username);

    /**
     * 校验用户名是否唯一（排除指定 id）。
     *
     * @param userId   当前用户 id（新增传 null）
     * @param username 用户名
     * @return 唯一返回 true
     */
    boolean checkUsernameUnique(Long userId, String username);

    /**
     * 分页列表查询（受数据权限约束：本部门/本部门及以下/仅本人/自定义部门）。
     *
     * @param page 分页对象
     * @param query 查询条件
     * @return 分页结果
     */
    @DataScope(deptAlias = "u", deptColumn = "dept_id", userAlias = "u", userColumn = "user_id")
    IPage<SysUser> selectUserPage(IPage<SysUser> page, SysUserQuery query);
}
