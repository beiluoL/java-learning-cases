package com.learn.springmini.aop;

import com.learn.springmini.aop.framework.adapter.AfterReturningAdviceInterceptor;
import com.learn.springmini.aop.framework.adapter.MethodBeforeAdviceInterceptor;
import com.learn.springmini.aop.framework.adapter.ThrowsAdviceInterceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 被代理对象配置中心 —— 对应 Spring 的 {@code AdvisedSupport}。
 *
 * <p>它集中保存了“织入所需的一切信息”：目标对象、目标类、以及所有 {@link Advisor}。
 * 最关键的方法是 {@link #getInterceptors(Method)}：在每次方法调用时，
 * 根据当前方法<b>动态筛选</b>出匹配的 Advisor，并把各种通知统一“适配”成 {@link MethodInterceptor}，
 * 串成一条拦截器链。这正是 Spring AOP 运行时的核心。</p>
 */
public class AdvisedSupport {

    /** 目标对象（被代理的原始对象）。 */
    private Object target;

    /** 目标对象的类。 */
    private Class<?> targetClass;

    /** 所有 Advisor（来自 XML 配置或编程式添加）。 */
    private final List<Advisor> advisors = new ArrayList<>();

    public void setTarget(Object target) {
        this.target = target;
        this.targetClass = target.getClass();
    }

    public Object getTarget() {
        return target;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }

    public void addAdvisor(Advisor advisor) {
        advisors.add(advisor);
    }

    public List<Advisor> getAdvisors() {
        return advisors;
    }

    /**
     * 为指定方法构建拦截器链。
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>遍历所有 Advisor，用其 Pointcut 判断<b>当前方法是否命中</b>（类过滤 + 方法匹配）；</li>
     *   <li>命中的，把 Advice 适配为 {@link MethodInterceptor}（前置/后置/异常都包成拦截器）；</li>
     *   <li>按“前置→环绕→后置返回→异常”的顺序排序，决定链上的嵌套关系。</li>
     * </ol>
     */
    public List<MethodInterceptor> getInterceptors(Method method) {
        List<AdviceHolder> holders = new ArrayList<>();
        for (Advisor advisor : advisors) {
            Pointcut pc = ((PointcutAdvisor) advisor).getPointcut();
            if (!pc.getClassFilter().matches(targetClass)) {
                continue;
            }
            if (!pc.getMethodMatcher().matches(method, targetClass)) {
                continue;
            }
            holders.add(new AdviceHolder(advisor.getAdvice()));
        }
        holders.sort(Comparator.comparingInt(h -> h.order));

        List<MethodInterceptor> chain = new ArrayList<>(holders.size());
        for (AdviceHolder h : holders) {
            chain.add(wrap(h.advice));
        }
        return chain;
    }

    /** 把不同类型的 Advice 统一包装成 MethodInterceptor，使它们都能串到同一条链上。 */
    private static MethodInterceptor wrap(Advice advice) {
        if (advice instanceof MethodInterceptor) {
            return (MethodInterceptor) advice;
        }
        if (advice instanceof MethodBeforeAdvice) {
            return new MethodBeforeAdviceInterceptor((MethodBeforeAdvice) advice);
        }
        if (advice instanceof AfterReturningAdvice) {
            return new AfterReturningAdviceInterceptor((AfterReturningAdvice) advice);
        }
        if (advice instanceof ThrowsAdvice) {
            return new ThrowsAdviceInterceptor((ThrowsAdvice) advice);
        }
        throw new AopConfigException("未知的通知类型: " + advice.getClass().getName());
    }

    /** 内部持有“通知 + 排序权重”，用于链的顺序控制。 */
    private static final class AdviceHolder {
        final Advice advice;
        final int order;

        AdviceHolder(Advice advice) {
            this.advice = advice;
            this.order = orderOf(advice);
        }

        /** 前置=1，环绕=2，后置返回=3，异常=4（值越小越“外层”）。 */
        private static int orderOf(Advice advice) {
            if (advice instanceof MethodBeforeAdvice) {
                return 1;
            }
            if (advice instanceof MethodInterceptor) {
                return 2;
            }
            if (advice instanceof AfterReturningAdvice) {
                return 3;
            }
            if (advice instanceof ThrowsAdvice) {
                return 4;
            }
            return 99;
        }
    }
}
