package com.learn.springmini.aop;

/**
 * 代理工厂 —— 对应 Spring 的 {@code ProxyFactory}。
 *
 * <p>对使用者屏蔽了“到底用 JDK 动态代理还是 CGLIB”的细节，只需：</p>
 * <pre>
 *   ProxyFactory pf = new ProxyFactory();
 *   pf.setTarget(目标对象);
 *   pf.addAdvisor(advisor);
 *   Object proxy = pf.getProxy();   // 内部自动选择代理方式
 * </pre>
 *
 * <h3>选择策略（与 Spring 默认行为一致）</h3>
 * <ul>
 *   <li>目标<b>实现了接口</b> → 使用 JDK 动态代理（基于接口）；</li>
 *   <li>目标<b>是具体类、没有接口</b> → 使用 CGLIB（基于继承，生成子类）。</li>
 * </ul>
 */
public class ProxyFactory {

    private final AdvisedSupport advised = new AdvisedSupport();

    public void setTarget(Object target) {
        advised.setTarget(target);
    }

    public void addAdvisor(Advisor advisor) {
        advised.addAdvisor(advisor);
    }

    /** 生成代理对象（自动选择 JDK / CGLIB）。 */
    public Object getProxy() {
        return createAopProxy().getProxy();
    }

    private AopProxy createAopProxy() {
        Class<?> targetClass = advised.getTargetClass();
        // 只要目标实现了接口，就走 JDK 动态代理；否则走 CGLIB
        if (targetClass.getInterfaces().length > 0) {
            return new com.learn.springmini.aop.framework.JdkDynamicAopProxy(advised);
        }
        return new com.learn.springmini.aop.framework.CglibAopProxy(advised);
    }
}
