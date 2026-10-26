package com.enterprise.erpcrm.framework.security.datascope;

import java.util.List;

/**
 * 部门数据仓储契约（依赖倒置）：数据权限切面需要查询「部门及其下级」或「自定义部门」，
 * 具体实现由 system 模块提供（基于 sys_dept / sys_role_dept），framework 仅依赖此接口，避免循环依赖。
 *
 * @author erp-crm-system
 */
public interface DeptDataRepository {

    /** 取得某部门自身及其所有下级部门 id（基于 sys_dept.ancestors 路径） */
    List<Long> selectChildDeptIds(Long deptId);

    /** 取得某用户「自定义数据权限」可见的部门 id（来自 sys_role_dept，role.data_scope=2） */
    List<Long> selectCustomDeptIds(Long userId);
}
