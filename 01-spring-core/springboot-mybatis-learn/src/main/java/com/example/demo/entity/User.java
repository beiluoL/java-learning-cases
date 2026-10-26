package com.example.demo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库 user 表。
 *
 * <p>字段命名遵循阿里规范：数据库下划线（user_name）与 Java 驼峰（userName）通过
 * mybatis map-underscore-to-camel-case 自动映射。
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;

    /** 用户名 */
    private String userName;

    /** 年龄 */
    private Integer age;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除：1=已删除，0=未删除 */
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "User{"
                + "id=" + id
                + ", userName='" + userName + '\''
                + ", age=" + age
                + ", createTime=" + createTime
                + ", updateTime=" + updateTime
                + ", deleted=" + deleted
                + '}';
    }
}
