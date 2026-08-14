package com.learn.mybatismini.reflection.invoker;

import java.lang.reflect.Field;

/**
 * 基于<b>字段</b>直接写入的访问器（属性没有 setter 时的兜底方案）。
 *
 * <p>典型场景：用 Lombok 的 {@code @Getter} 但忘了 {@code @Setter}，
 * 或者实体是不可变对象。有了它，MyBatis 依然能把结果集塞进对象。
 *
 * @author mybatis-source-learn
 */
public class SetFieldInvoker implements Invoker {

    private final Field field;

    public SetFieldInvoker(Field field) {
        this.field = field;
        this.field.setAccessible(true);
    }

    @Override
    public Object invoke(Object target, Object[] args) throws Exception {
        field.set(target, args[0]);
        return null;
    }

    @Override
    public Class<?> getType() {
        return field.getType();
    }
}
