package com.learn.springmini.aop.pointcut;

import com.learn.springmini.aop.ClassFilter;
import com.learn.springmini.aop.MethodMatcher;
import com.learn.springmini.aop.Pointcut;

import java.lang.reflect.Method;

/**
 * 按方法名匹配的切点 —— 对应 Spring 的 {@code NameMatchMethodPointcut}。
 *
 * <p>支持两种写法：</p>
 * <ul>
 *   <li>精确匹配：{@code mappedName="createUser"} → 仅匹配名为 createUser 的方法；</li>
 *   <li>前缀通配：{@code mappedName="find*"} → 匹配所有以 find 开头的方法。</li>
 * </ul>
 * 它是手写版里最简单直观的 Pointcut，足以演示“切点如何筛选要增强的方法”。
 * 真实 Spring 中还有更强大的 {@code AspectJExpressionPointcut}（支持 execution/annotation 等表达式）。
 */
public class NameMatchMethodPointcut implements Pointcut {

    /** 匹配规则：精确方法名，或以 '*' 结尾的前缀。 */
    private String mappedName = null;

    /** 类级别过滤，默认对所有类放行。 */
    private ClassFilter classFilter = ClassFilter.TRUE;

    public void setMappedName(String mappedName) {
        this.mappedName = mappedName;
    }

    public void setClassFilter(ClassFilter classFilter) {
        this.classFilter = classFilter;
    }

    @Override
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    @Override
    public MethodMatcher getMethodMatcher() {
        return new NameMatchMethodMatcher();
    }

    /** 内部方法匹配器实现。 */
    private class NameMatchMethodMatcher implements MethodMatcher {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return matchesName(method.getName());
        }
    }

    private boolean matchesName(String methodName) {
        if (mappedName == null) {
            return false;
        }
        // 后缀通配：如 "*User" 表示“以 User 结尾”（匹配 createUser、findUser）
        if (mappedName.startsWith("*") && !mappedName.endsWith("*")) {
            String suffix = mappedName.substring(1);
            return methodName.endsWith(suffix);
        }
        // 前缀通配：如 "find*" 表示“以 find 开头”
        if (mappedName.endsWith("*")) {
            String prefix = mappedName.substring(0, mappedName.length() - 1);
            return methodName.startsWith(prefix);
        }
        // 精确匹配
        return methodName.equals(mappedName);
    }
}
