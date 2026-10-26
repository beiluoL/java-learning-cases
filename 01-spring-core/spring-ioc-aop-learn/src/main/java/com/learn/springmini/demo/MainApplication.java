package com.learn.springmini.demo;

import com.learn.springmini.demo.service.OrderServiceImpl;
import com.learn.springmini.demo.service.UserService;
import com.learn.springmini.ioc.ClassPathXmlApplicationContext;

/**
 * 整合演示入口（IoC + AOP 自动织入）。
 *
 * <p>运行：</p>
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.learn.springmini.demo.MainApplication
 * </pre>
 *
 * <p>这是最“Spring 风格”的用法：我们只声明 Bean 和 Advisor，容器在启动时自动把 AOP 织入。
 * 从容器取出来的 userService / orderService，其实已经是被切面增强过的代理对象。</p>
 */
public class MainApplication {

    public static void main(String[] args) {
        // 启动容器（ApplicationContext）：加载 XML → 实例化 → 注入 → AOP 自动代理
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("aop-demo.xml");

        System.out.println("========== 整合演示：IoC 容器中的 Bean 已被 AOP 自动增强 ==========");

        // —— 有接口的目标：JDK 动态代理 ——
        UserService userService = ctx.getBean("userService", UserService.class);
        System.out.println("[userService] 实际类型: " + userService.getClass().getName());
        userService.createUser("王五");
        System.out.println("findUser(1) => " + userService.findUser(1));

        // 触发异常通知
        System.out.println("\n--- 触发异常通知（findUser 传入非法 id）---");
        try {
            userService.findUser(-1);
        } catch (Exception e) {
            System.out.println("[主程序] 捕获到异常: " + e.getClass().getSimpleName() + " -> " + e.getMessage());
        }

        // —— 无接口的目标：CGLIB 动态代理 ——
        OrderServiceImpl orderService = ctx.getBean("orderService", OrderServiceImpl.class);
        System.out.println("\n[orderService] 实际类型: " + orderService.getClass().getName());
        orderService.placeOrder("Spring 源码深度解析");
    }
}
