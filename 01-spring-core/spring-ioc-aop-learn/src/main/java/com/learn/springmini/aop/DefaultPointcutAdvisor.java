package com.learn.springmini.aop;

/**
 * 通用通知器实现 —— 对应 Spring 的 {@code DefaultPointcutAdvisor}。
 *
 * <p>把任意一个 {@link Pointcut} 和任意一个 {@link Advice} 组合成一个 Advisor。
 * 在 XML 中可直接这样声明：</p>
 * <pre>{@code
 * <bean id="beforeAdvisor" class="com.learn.springmini.aop.DefaultPointcutAdvisor">
 *     <constructor-arg ref="pcCreateUser"/>
 *     <constructor-arg ref="logBeforeAdvice"/>
 * </bean>
 * }</pre>
 */
public class DefaultPointcutAdvisor implements PointcutAdvisor {

    private Pointcut pointcut;
    private Advice advice;

    public DefaultPointcutAdvisor() {
    }

    public DefaultPointcutAdvisor(Pointcut pointcut, Advice advice) {
        this.pointcut = pointcut;
        this.advice = advice;
    }

    public void setPointcut(Pointcut pointcut) {
        this.pointcut = pointcut;
    }

    public void setAdvice(Advice advice) {
        this.advice = advice;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }
}
