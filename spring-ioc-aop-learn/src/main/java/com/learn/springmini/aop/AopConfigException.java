package com.learn.springmini.aop;

/**
 * AOP 配置非法时抛出的异常（如：对没有接口的类却要用 JDK 动态代理）。
 */
public class AopConfigException extends RuntimeException {

    public AopConfigException(String message) {
        super(message);
    }
}
