package com.learn.springmini.demo.dao;

/**
 * 数据访问层接口（DAO）。
 * 用接口 + 实现类，是为了演示“面向接口编程”下 IoC 的依赖注入与 AOP 的 JDK 动态代理。
 */
public interface UserDao {

    /** 模拟把用户保存到数据库。 */
    void save(String username);
}
