package com.enterprise.erpcrm.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.enterprise.erpcrm.module.system.entity.SysDept;
import java.util.List;

/**
 * 系统部门服务接口。
 *
 * @author erp-crm-system
 */
public interface SysDeptService extends IService<SysDept> {

    /**
     * 构建部门树。
     *
     * @return 一级部门（含 children）
     */
    List<SysDept> buildTree();

    /**
     * 根据部门 id 查询其「本部门及以下」的全部部门 id（含自身）。
     *
     * @param deptId 部门 id
     * @return 部门 id 列表
     */
    List<Long> selectChildDeptIds(Long deptId);
}
