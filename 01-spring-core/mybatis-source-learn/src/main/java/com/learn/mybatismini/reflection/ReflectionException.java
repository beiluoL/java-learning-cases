package com.learn.mybatismini.reflection;

import com.learn.mybatismini.exceptions.PersistenceException;

/**
 * 反射子系统专用异常。
 *
 * @author mybatis-source-learn
 */
public class ReflectionException extends PersistenceException {

    private static final long serialVersionUID = 1L;

    public ReflectionException(String message) {
        super(message);
    }

    public ReflectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
