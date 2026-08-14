package com.learn.springmini.demo;

import com.learn.springmini.aop.DefaultPointcutAdvisor;
import com.learn.springmini.aop.ProxyFactory;
import com.learn.springmini.aop.pointcut.NameMatchMethodPointcut;
import com.learn.springmini.demo.aspect.LogAroundInterceptor;
import com.learn.springmini.demo.aspect.LogMethodBeforeAdvice;
import com.learn.springmini.demo.dao.UserDaoImpl;
import com.learn.springmini.demo.service.OrderServiceImpl;
import com.learn.springmini.demo.service.UserService;
import com.learn.springmini.demo.service.UserServiceImpl;

/**
 * 纯 AOP 演示入口（编程式，不依赖 IoC 容器）。
 *
 * <p>运行：</p>
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.learn.springmini.demo.AopDemo
 * </pre>
 *
 * <p>本例用 {@link ProxyFactory} 手动织入，直观展示：</p>
 * <ul>
 *   <li>对“有接口”的目标 → 生成 <b>JDK 动态代理</b>（实现接口）；</li>
 *   <li>对“无接口的具体类” → 生成 <b>CGLIB 动态代理</b>（继承子类）。</li>
 * </ul>
 */
public class AopDemo {

    public static void main(String[] args) {
        System.out.println("========== AOP 演示：ProxyFactory 编程式织入 ==========");

        // ---------- 1) JDK 动态代理：目标实现了接口 ----------
        UserServiceImpl target = new UserServiceImpl(new UserDaoImpl());
        target.setAppName("编程式-AOP");

        NameMatchMethodPointcut pc = new NameMatchMethodPointcut();
        pc.setMappedName("createUser");
        ProxyFactory pf = new ProxyFactory();
        pf.setTarget(target);
        pf.addAdvisor(new DefaultPointcutAdvisor(pc, new LogMethodBeforeAdvice()));

        Object proxy = pf.getProxy();
        System.out.println("[JDK] 代理类: " + proxy.getClass().getName());
        System.out.println("[JDK] 是否 JDK Proxy: " + java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));

        UserService userService = (UserService) proxy;
        userService.createUser("李四");

        // ---------- 2) CGLIB 动态代理：目标是不实现接口的具体类 ----------
        System.out.println("\n--- CGLIB 动态代理（无接口的具体类）---");
        OrderServiceImpl orderTarget = new OrderServiceImpl();
        orderTarget.setShopName("码上书店");

        ProxyFactory pf2 = new ProxyFactory();
        pf2.setTarget(orderTarget);
        NameMatchMethodPointcut pc2 = new NameMatchMethodPointcut();
        pc2.setMappedName("placeOrder");
        pf2.addAdvisor(new DefaultPointcutAdvisor(pc2, new LogAroundInterceptor()));

        OrderServiceImpl orderProxy = (OrderServiceImpl) pf2.getProxy();
        System.out.println("[CGLIB] 代理类: " + orderProxy.getClass().getName());
        System.out.println("[CGLIB] 是否为 CGLIB Enhancer 子类: "
                + orderProxy.getClass().getName().contains("Enhancer"));
        orderProxy.placeOrder("Java 编程思想");
    }
}
