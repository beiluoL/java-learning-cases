package com.learn.springmini.demo.service;

/**
 * 用户业务接口。实现类 {@link UserServiceImpl} 既演示<b>构造器注入</b>（注入 DAO 依赖），
 * 又演示<b>Setter 注入</b>（注入配置项），同时因为实现了本接口，会被 AOP 用 JDK 动态代理增强。
 */
public interface UserService {

    /** 创建用户。 */
    void createUser(String username);

    /** 根据 id 查询用户（id<=0 时抛异常，用于演示异常通知）。 */
    String findUser(int id);
}
