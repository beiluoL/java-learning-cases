package com.learn.springmini.demo.dao;

/**
 * UserDao 的实现类。真正干“持久化”的活。
 */
public class UserDaoImpl implements UserDao {

    @Override
    public void save(String username) {
        System.out.println("    [UserDaoImpl] 持久化用户到数据库: " + username);
    }
}
