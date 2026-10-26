package com.learn.springmini.demo.aspect;

import com.learn.springmini.aop.AfterReturningAdvice;

import java.lang.reflect.Method;

/**
 * 自定义【后置返回通知】—— 在目标方法<b>正常返回后</b>打印返回值日志。
 *
 * <p>实现 {@link AfterReturningAdvice}。注意：目标方法抛异常时它不会执行。</p>
 */
public class LogAfterReturningAdvice implements AfterReturningAdvice {

    @Override
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) {
        System.out.println("  [后置通知] 方法 " + method.getName()
                + " 执行完毕，返回值=" + returnValue);
    }
}
