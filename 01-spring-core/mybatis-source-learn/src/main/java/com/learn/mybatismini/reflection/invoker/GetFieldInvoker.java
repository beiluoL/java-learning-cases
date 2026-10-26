package com.learn.mybatismini.reflection.invoker;

import java.lang.reflect.Field;

/**
 * 基于<b>字段</b>直接读取的访问器（属性没有 getter 时的兜底方案）。
 *
 * @author mybatis-source-learn
 */
public class GetFieldInvoker implements Invoker {

    private final Field field;

    public GetFieldInvoker(Field field) {
        this.field = field;
        // 绕过访问修饰符检查，才能读 private 字段
        this.field.setAccessible(true);
    }

    @Override
    public Object invoke(Object target, Object[] args) throws Exception {
        return field.get(target);
    }

    @Override
    public Class<?> getType() {
        return field.getType();
    }
}
