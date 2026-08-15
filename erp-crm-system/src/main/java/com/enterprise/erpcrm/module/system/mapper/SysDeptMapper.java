package com.enterprise.erpcrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.system.entity.SysDept;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统部门 Mapper。提供数据权限所需的「部门及其下级」查询。
 *
 * @author erp-crm-system
 */
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 部门自身 + 所有下级部门 id（ancestors 路径匹配） */
    @Select("SELECT id FROM sys_dept "
            + "WHERE deleted = 0 AND (id = #{deptId} OR ancestors LIKE CONCAT('%', #{deptId}, '%'))")
    List<Long> selectChildDeptIds(@Param("deptId") Long deptId);
}
