package com.learn.springmini.ioc.util;

import com.learn.springmini.ioc.BeansException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 反射工具 —— 负责“按名字找 setter”和“按参数匹配构造函数”等底层反射操作。
 *
 * <p>依赖注入本质上就是反射：找到对应的方法/构造器，再用 {@code invoke} 把值塞进去。
 * 这里把反射的脏活集中封装，让容器主流程更清晰。</p>
 */
public final class BeanUtils {

    private BeanUtils() {
    }

    /**
     * 根据属性名找到对应的 setter 方法（如 "appName" → {@code setAppName}）。
     * 该方法必须有且仅有一个参数。
     */
    public static Method findSetter(Class<?> beanClass, String propertyName) {
        String setterName = "set" + capitalize(propertyName);
        for (Method method : beanClass.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new BeansException("在类 " + beanClass.getName()
                + " 上找不到属性 '" + propertyName + "' 对应的 setter 方法 (" + setterName + ")");
    }

    /** 首字母大写（用于 setName 拼接）。 */
    public static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 在候选构造函数中，挑出与给定实参数量和类型都匹配的构造函数。
     *
     * <p>匹配规则（与 Spring 的构造器解析思路一致）：</p>
     * <ol>
     *   <li>参数个数必须相等；</li>
     *   <li>逐个位置判断：实参类型可否赋值给形参类型（引用注入时实参已是目标 Bean 实例）。</li>
     * </ol>
     *
     * @param beanClass 目标类
     * @param args      已解析好的实参（引用已替换为真实对象，字面量仍是 String）
     */
    public static Constructor<?> findMatchingConstructor(Class<?> beanClass, Object[] args) {
        Constructor<?>[] candidates = beanClass.getDeclaredConstructors();
        for (Constructor<?> candidate : candidates) {
            Class<?>[] paramTypes = candidate.getParameterTypes();
            if (paramTypes.length != args.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    // null 可匹配任何引用类型，但不匹配基本类型
                    if (paramTypes[i].isPrimitive()) {
                        match = false;
                        break;
                    }
                    continue;
                }
                if (!paramTypes[i].isInstance(arg)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return candidate;
            }
        }
        throw new BeansException("在类 " + beanClass.getName()
                + " 上找不到与给定参数匹配的构造函数（参数个数=" + args.length + "）");
    }
}
