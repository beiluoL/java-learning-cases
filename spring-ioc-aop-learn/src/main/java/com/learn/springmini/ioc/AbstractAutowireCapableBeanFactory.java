package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;
import com.learn.springmini.ioc.beans.BeanReference;
import com.learn.springmini.ioc.beans.ConstructorArgumentValue;
import com.learn.springmini.ioc.beans.ConstructorArgumentValues;
import com.learn.springmini.ioc.beans.PropertyValue;
import com.learn.springmini.ioc.util.BeanUtils;
import com.learn.springmini.ioc.util.TypeConverter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 可自动装配的 Bean 工厂 —— 对应 Spring 的 {@code AbstractAutowireCapableBeanFactory}。
 *
 * <p>这里是 IoC 真正“干活”的地方，一个 Bean 从诞生到可用经历了完整生命周期：</p>
 * <pre>
 *   createBean
 *     ├─ 1. 实例化 instantiateBean   （构造器注入 / 无参构造）
 *     ├─ 2. 属性填充 populateBean     （Setter 注入）
 *     └─ 3. 初始化 initializeBean
 *            ├─ Aware 回调（BeanFactoryAware）
 *            ├─ InitializingBean.afterPropertiesSet()
 *            ├─ BeanPostProcessor 前置
 *            └─ BeanPostProcessor 后置  ← AOP 自动代理在这里把 Bean 换成代理
 * </pre>
 * 依赖注入（DI）的两种主流方式——<b>构造器注入</b>与<b>Setter 注入</b>——都在本类中实现。
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory {

    /** 容器中的后置处理器列表，按注册顺序执行。 */
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    // ===================== 对外：注册后置处理器 =====================

    public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
        // 避免重复注册
        beanPostProcessors.remove(beanPostProcessor);
        beanPostProcessors.add(beanPostProcessor);
    }

    public List<BeanPostProcessor> getBeanPostProcessors() {
        return beanPostProcessors;
    }

    // ===================== 模板方法：创建 Bean =====================

    @Override
    protected Object createBean(String beanName, BeanDefinition bd) throws BeansException {
        Class<?> beanClass = resolveBeanClass(bd);

        // 1) 实例化（可能涉及构造器注入）
        Object bean = createBeanInstance(beanName, bd, beanClass);

        // 2) 属性填充（Setter 注入）
        populateBean(bean, bd, beanClass);

        // 3) 初始化（Aware / 生命周期 / 后置处理器）
        bean = initializeBean(beanName, bean, beanClass);

        // 4) 单例：放入缓存，保证“下次 getBean 拿到的还是同一个”
        if (bd.isSingleton()) {
            registerSingleton(beanName, bean);
        }
        return bean;
    }

    // ===================== 步骤 1：实例化 =====================

    private Object createBeanInstance(String beanName, BeanDefinition bd, Class<?> beanClass) {
        ConstructorArgumentValues cav = bd.getConstructorArgumentValues();
        if (cav.isEmpty()) {
            // 情况 A：无构造参数 → 直接调无参构造
            try {
                return beanClass.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                throw new BeansException("类 " + beanClass.getName()
                        + " 没有可用的无参构造函数，且未配置 <constructor-arg>", e);
            } catch (Exception e) {
                throw new BeansException("实例化 Bean '" + beanName + "' 失败", e);
            }
        }
        // 情况 B：配置了构造参数 → 构造器注入
        return instantiateWithConstructor(beanName, bd, beanClass, cav);
    }

    /** 构造器注入：按 index 准备实参 → 匹配构造函数 → 反射调用。 */
    private Object instantiateWithConstructor(String beanName, BeanDefinition bd,
                                              Class<?> beanClass, ConstructorArgumentValues cav) {
        // 1. 把每个构造参数的值解析出来（BeanReference → 递归 getBean；字面量保留 String）
        List<ConstructorArgumentValue> sorted = cav.getSortedValues();
        Object[] args = new Object[sorted.size()];
        Class<?>[] argTypes = new Class<?>[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            ConstructorArgumentValue cavItem = sorted.get(i);
            args[i] = resolveValue(cavItem.getValue());
            argTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }

        // 2. 找到与实参匹配的构造函数
        Constructor<?> constructor = BeanUtils.findMatchingConstructor(beanClass, args);

        // 3. 把字面量 String 实参转换成构造函数形参要求的精确类型（如 String→int）
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] convertedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertedArgs[i] = TypeConverter.convertIfNecessary(args[i], paramTypes[i]);
        }

        // 4. 反射创建实例
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(convertedArgs);
        } catch (Exception e) {
            throw new BeansException("通过构造函数实例化 Bean '" + beanName + "' 失败", e);
        }
    }

    // ===================== 步骤 2：属性填充（Setter 注入） =====================

    private void populateBean(Object bean, BeanDefinition bd, Class<?> beanClass) {
        if (bd.getPropertyValues().isEmpty()) {
            return;
        }
        for (PropertyValue pv : bd.getPropertyValues().getPropertyValues()) {
            // 解析属性值（引用 → 真实对象；字面量 → String）
            Object value = resolveValue(pv.getValue());

            // 找到 setter 方法
            Method setter = BeanUtils.findSetter(beanClass, pv.getName());

            // 把值转换成 setter 形参要求的类型
            Class<?> paramType = setter.getParameterTypes()[0];
            Object converted = TypeConverter.convertIfNecessary(value, paramType);

            // 反射调用 setter 完成注入
            try {
                setter.invoke(bean, converted);
            } catch (Exception e) {
                throw new BeansException("为 Bean 的属性 '" + pv.getName() + "' 注入值失败", e);
            }
        }
    }

    // ===================== 步骤 3：初始化 =====================

    private Object initializeBean(String beanName, Object bean, Class<?> beanClass) {
        // 3.1 Aware 回调：如果 Bean 想知道容器是谁，告诉它
        if (bean instanceof BeanFactoryAware aware) {
            aware.setBeanFactory(this);
        }

        // 3.2 InitializingBean 生命周期回调
        if (bean instanceof InitializingBean initializingBean) {
            try {
                initializingBean.afterPropertiesSet();
            } catch (Exception e) {
                throw new BeansException("Bean '" + beanName + "' 的 afterPropertiesSet 执行失败", e);
            }
        }

        // 3.3 后置处理器——初始化前
        for (BeanPostProcessor bpp : getBeanPostProcessors()) {
            bean = bpp.postProcessBeforeInitialization(bean, beanName);
        }

        // 3.4 后置处理器——初始化后（★ AOP 自动代理在这里把原始 Bean 替换成代理对象）
        for (BeanPostProcessor bpp : getBeanPostProcessors()) {
            bean = bpp.postProcessAfterInitialization(bean, beanName);
        }
        return bean;
    }

    // ===================== 公共辅助 =====================

    /** 把配置中的值解析成运行时对象：引用则递归取 Bean，字面量原样返回。 */
    protected Object resolveValue(Object rawValue) {
        if (rawValue instanceof BeanReference ref) {
            // 依赖注入的核心：遇到引用就向容器要那个 Bean（可能会触发它的创建）
            return getBean(ref.getBeanName());
        }
        return rawValue;
    }

    /** 根据类名加载 Class（用上下文类加载器）。 */
    protected Class<?> resolveBeanClass(BeanDefinition bd) {
        try {
            return Class.forName(bd.getBeanClassName(), true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new BeansException("找不到 Bean 类: " + bd.getBeanClassName(), e);
        }
    }

    /** 由子类（DefaultListableBeanFactory）实现按类型批量查询 Bean 的能力。 */
    public abstract <T> Map<String, T> getBeansOfType(Class<T> type);
}
