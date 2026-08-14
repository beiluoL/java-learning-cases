package com.learn.mybatismini.demo.entity;

import java.time.LocalDateTime;

/**
 * 用户实体（对应表 t_user）。
 *
 * <p>请特别注意属性命名与表字段的对应关系：
 * <pre>
 *   表字段（H2 MODE=MySQL 返回大写）   实体属性（驼峰）
 *   USER_NAME                    ->   userName
 *   CREATE_TIME                  ->   createTime
 * </pre>
 * 因为我们开启了 {@code mapUnderscoreToCamelCase=true}，下划线字段会自动映射到驼峰属性，
 * 否则需要手写 {@code <resultMap>} 逐列指定。
 *
 * <p>另外：实体必须保留一个 <b>无参构造器</b>（反射实例化结果对象用），
 * 并且每个要映射的属性都要有标准的 getter/setter（反射赋值时用）。
 */
public class User {

    private Long id;

    private String userName;

    private Integer age;

    private String email;

    private LocalDateTime createTime;

    public User() {
    }

    public User(String userName, Integer age, String email) {
        this.userName = userName;
        this.age = age;
        this.email = email;
    }

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", userName='" + userName + '\'' + ", age=" + age
                + ", email='" + email + '\'' + ", createTime=" + createTime + '}';
    }
}
