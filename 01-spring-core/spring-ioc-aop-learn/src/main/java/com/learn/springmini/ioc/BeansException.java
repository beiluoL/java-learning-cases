package com.learn.springmini.ioc;

/**
 * IoC 容器中所有异常的基类。
 *
 * <p>对应 Spring 中的 {@code org.springframework.beans.BeansException}。
 * 我们手写时只保留一个统一的基类，初学者看到异常栈就能定位到“容器层面”的问题。</p>
 */
public class BeansException extends RuntimeException {

    public BeansException(String message) {
        super(message);
    }

    public BeansException(String message, Throwable cause) {
        super(message, cause);
    }
}
