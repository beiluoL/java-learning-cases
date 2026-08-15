package com.enterprise.erpcrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper。
 *
 * @author erp-crm-system
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
