package com.ecommerce.learn.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /** 登录名（唯一） */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    private String phone;

    private String email;

    private String avatar;

    /** 角色：USER / ADMIN */
    private String role;

    /** 状态：1 正常，0 禁用 */
    private Integer status;
}
