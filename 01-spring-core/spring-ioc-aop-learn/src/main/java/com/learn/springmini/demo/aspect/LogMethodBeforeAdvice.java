package com.learn.springmini.demo.aspect;

import com.learn.springmini.aop.MethodBeforeAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 自定义【前置通知】—— 在目标方法执行前打印日志。
 *
 * <p>实现 {@link MethodBeforeAdvice} 即可被容器识别为“前置通知”。
 * 在真实项目里，这类逻辑常用于：权限校验、入参日志、分布式锁加锁等。</p>
 */
public class LogMethodBeforeAdvice implements MethodBeforeAdvice {

    @Override
    public void before(Method method, Object[] args, Object target) {
        System.out.println("  [前置通知] 即将执行方法: " + method.getName()
                + "，参数=" + Arrays.toString(args));
    }
}
