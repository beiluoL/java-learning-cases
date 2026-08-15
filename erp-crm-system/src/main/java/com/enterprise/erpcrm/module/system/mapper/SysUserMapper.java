package com.enterprise.erpcrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.enterprise.erpcrm.module.system.domain.query.SysUserQuery;
import com.enterprise.erpcrm.module.system.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper。
 *
 * @author erp-crm-system
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按登录名查询（登录用，含逻辑删除过滤由 MP 全局逻辑删除处理） */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(@Param("username") String username);

    /** 分页列表：左联部门取部门名，支持数据权限片段 dataScope（来自 DataScopeContext） */
    IPage<SysUser> selectUserPage(IPage<SysUser> page,
                                  @Param("q") SysUserQuery query,
                                  @Param("dataScope") String dataScope);

    /** 统计某部门下的用户数（删除部门前校验用） */
    @Select("SELECT COUNT(1) FROM sys_user WHERE dept_id = #{deptId}")
    int countByDeptId(@Param("deptId") Long deptId);
}
