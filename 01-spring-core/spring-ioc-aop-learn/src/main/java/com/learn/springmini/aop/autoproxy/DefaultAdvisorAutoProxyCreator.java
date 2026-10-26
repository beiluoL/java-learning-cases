package com.learn.springmini.aop.autoproxy;

import com.learn.springmini.aop.Advisor;
import com.learn.springmini.aop.Pointcut;
import com.learn.springmini.aop.PointcutAdvisor;
import com.learn.springmini.aop.ProxyFactory;
import com.learn.springmini.aop.Advice;
import com.learn.springmini.ioc.BeanFactory;
import com.learn.springmini.ioc.BeanFactoryAware;
import com.learn.springmini.ioc.BeanPostProcessor;
import com.learn.springmini.ioc.ListableBeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Advisor 的自动代理创建器 —— 对应 Spring 的 {@code DefaultAdvisorAutoProxyCreator}。
 *
 * <h3>它是 IoC 与 AOP 的“缝合点”</h3>
 * 它实现了 IoC 的 {@link BeanPostProcessor}。在容器把每个 Bean 创建、注入、初始化完毕之后，
 * 会回调它的 {@link #postProcessAfterInitialization}。在这里它：</p>
 * <ol>
 *   <li>从容器中找出所有 {@link Advisor}（切点+通知）；</li>
 *   <li>判断当前 Bean 是否有方法被某个 Advisor 的切点命中；</li>
 *   <li>若命中，则用 {@link ProxyFactory} 把原始 Bean 包装成 AOP 代理返回。</li>
 * </ol>
 * <p>于是调用方从容器里 {@code getBean} 拿到的，已经是“被切面增强过的代理对象”了。
 * 这正是 Spring 中 {@code @EnableAspectJAutoProxy} 背后自动织入的原理。</p>
 */
public class DefaultAdvisorAutoProxyCreator implements BeanPostProcessor, BeanFactoryAware {

    /** 容器引用（Aware 注入），用于查询所有 Advisor。 */
    private ListableBeanFactory beanFactory;

    /** 缓存查到的 Advisor 列表，避免每次都重新扫描。 */
    private List<Advisor> cachedAdvisors;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (!(beanFactory instanceof ListableBeanFactory)) {
            throw new IllegalStateException("DefaultAdvisorAutoProxyCreator 需要 ListableBeanFactory");
        }
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 1) AOP 基础设施自身不参与代理（避免自引用/无限递归）
        if (isAopInfrastructure(bean)) {
            return bean;
        }
        // 2) 取出所有 Advisor
        List<Advisor> advisors = getAdvisors();
        if (advisors.isEmpty()) {
            return bean;
        }
        // 3) 判断当前 Bean 是否有方法需要被增强
        if (!hasMatchableAdvisor(bean.getClass(), advisors)) {
            return bean;
        }
        // 4) 有命中 → 创建 AOP 代理替换原始 Bean
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(bean);
        for (Advisor advisor : advisors) {
            proxyFactory.addAdvisor(advisor);
        }
        return proxyFactory.getProxy();
    }

    /** 从容器获取全部 Advisor（首次调用后缓存）。 */
    private List<Advisor> getAdvisors() {
        if (cachedAdvisors == null) {
            cachedAdvisors = new ArrayList<>(beanFactory.getBeansOfType(Advisor.class).values());
        }
        return cachedAdvisors;
    }

    /** 判断某类中是否存在被任一 Advisor 命中的方法。 */
    private boolean hasMatchableAdvisor(Class<?> beanClass, List<Advisor> advisors) {
        for (Advisor advisor : advisors) {
            Pointcut pc = ((PointcutAdvisor) advisor).getPointcut();
            if (!pc.getClassFilter().matches(beanClass)) {
                continue;
            }
            for (Method method : beanClass.getMethods()) {
                if (pc.getMethodMatcher().matches(method, beanClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 判断一个对象是否为 AOP 基础设施（这些不应被再次代理）。 */
    private boolean isAopInfrastructure(Object bean) {
        return bean instanceof Advice
                || bean instanceof Pointcut
                || bean instanceof Advisor
                || bean instanceof BeanPostProcessor;
    }
}
