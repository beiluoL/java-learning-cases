package com.learn.springmini.demo.service;

import com.learn.springmini.demo.dao.UserDao;

/**
 * UserService 的实现类。
 *
 * <p>本类同时演示了 IoC 的两种依赖注入方式：</p>
 * <ul>
 *   <li><b>构造器注入</b>：{@link #UserServiceImpl(UserDao)} 接收 DAO 依赖（推荐用于“必需依赖”）；</li>
 *   <li><b>Setter 注入</b>：{@link #setAppName(String)} 接收配置项（推荐用于“可选依赖/配置”）。</li>
 * </ul>
 *
 * <p>因为本类实现了 {@link UserService} 接口，AOP 在增强它会走 <b>JDK 动态代理</b>（基于接口）。</p>
 */
public class UserServiceImpl implements UserService {

    /** 通过构造器注入的必需依赖。 */
    private final UserDao userDao;

    /** 通过 Setter 注入的可选配置项。 */
    private String appName;

    /** 构造器注入：容器在实例化时把 userDao 传进来。 */
    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    /** Setter 注入：容器在实例化后调用本方法塞入配置。 */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Override
    public void createUser(String username) {
        System.out.println("  [UserServiceImpl] 应用名=" + appName + "，开始创建用户: " + username);
        userDao.save(username);
        System.out.println("  [UserServiceImpl] 用户创建完成");
    }

    @Override
    public String findUser(int id) {
        if (id <= 0) {
            // 故意抛异常，给“异常通知”提供演示素材
            throw new IllegalArgumentException("用户 id 必须 > 0，但收到: " + id);
        }
        return "User-" + id;
    }
}
