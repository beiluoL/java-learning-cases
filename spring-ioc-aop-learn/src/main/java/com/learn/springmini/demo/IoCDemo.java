package com.learn.springmini.demo;

import com.learn.springmini.demo.service.UserService;
import com.learn.springmini.ioc.ClassPathXmlApplicationContext;

/**
 * 纯 IoC 演示入口。
 *
 * <p>运行：</p>
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.learn.springmini.demo.IoCDemo
 * </pre>
 *
 * <p>观察重点：</p>
 * <ul>
 *   <li>userService 并不是我们 {@code new} 出来的，而是向容器要的；</li>
 *   <li>它的 userDao 依赖、appName 配置，都已经被容器自动注入好了；</li>
 *   <li>createUser 内部能成功调用 userDao.save(...)，证明“依赖注入”已生效。</li>
 * </ul>
 */
public class IoCDemo {

    public static void main(String[] args) {
        // 1) 启动容器：读取 XML → 解析为 BeanDefinition → 实例化 + 注入
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("ioc-demo.xml");

        System.out.println("========== IoC 演示：构造器注入 + Setter 注入 ==========");
        // 2) 向容器要 Bean（依赖与配置均已就绪）
        UserService userService = ctx.getBean("userService", UserService.class);
        userService.createUser("张三");
        System.out.println("findUser(1) => " + userService.findUser(1));
    }
}
