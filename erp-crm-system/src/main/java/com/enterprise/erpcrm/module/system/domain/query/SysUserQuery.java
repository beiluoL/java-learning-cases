package com.enterprise.erpcrm.module.system.domain.query;

import com.enterprise.erpcrm.common.base.BasePageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户列表查询条件。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
public class SysUserQuery extends BasePageQuery {

    private static final long serialVersionUID = 1L;

    private String username;
    private String realName;
    private String phone;
    private Long deptId;
    private Integer status;
}
