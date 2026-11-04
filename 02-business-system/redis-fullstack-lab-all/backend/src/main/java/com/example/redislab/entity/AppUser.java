package com.example.redislab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 用户实体，对应 app_user 表。分布式会话场景以此演示登录态共享。
 *
 * <p>必须实现 {@link Serializable}：Spring Session 把会话属性以 Java 原生序列化方式写入 Redis，
 * 若实体不可序列化，login 写入 loginUser 时会抛 "Cannot serialize"（code=500）。</p>
 */
@TableName("app_user")
public class AppUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
