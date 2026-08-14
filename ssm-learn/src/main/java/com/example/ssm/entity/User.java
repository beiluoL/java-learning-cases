package com.example.ssm.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统用户实体，对应数据库表 sys_user。
 * 使用 Lombok @Data 自动生成 getter/setter/toString 等。
 */
@Data
public class User implements Serializable {

    /** 主键 */
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码（生产环境请加密存储） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 创建时间（@JsonFormat 控制返回前端的日期格式） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
