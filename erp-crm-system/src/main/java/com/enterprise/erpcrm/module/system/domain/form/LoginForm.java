package com.enterprise.erpcrm.module.system.domain.form;

import lombok.Data;

/**
 * 登录表单。
 *
 * @author erp-crm-system
 */
@Data
public class LoginForm {

    /** 用户名 */
    private String username;
    /** 明文密码 */
    private String password;
}
