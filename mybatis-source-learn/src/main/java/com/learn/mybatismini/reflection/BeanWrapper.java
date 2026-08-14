package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.invoker.Invoker;
import com.learn.mybatismini.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * JavaBean 包装器：通过 Reflector 缓存的 Invoker 读写属性。
 *
 * @author mybatis-source-learn
 */
public class BeanWrapper implements ObjectWrapper {

    private final Object object;
    private final Reflector reflector;
    /** 持有工厂引用，级联下钻时复用同一份缓存（不要在方法里 new，否则缓存失效） */
    private final ReflectorFactory reflectorFactory;

    public BeanWrapper(Object object, ReflectorFactory reflectorFactory) {
        this.object = object;
        this.reflectorFactory = reflectorFactory;
        this.reflector = reflectorFactory.findForClass(object.getClass());
    }

    @Override
    public Object get(PropertyTokenizer prop) {
        try {
            Object value = reflector.getGetInvoker(prop.getName()).invoke(object, null);
            // 形如 orders[0]：先取出集合，再按索引下钻
            if (prop.getIndex() != null) {
                return resolveIndexedValue(value, prop.getIndex());
            }
            return value;
        } catch (Exception e) {
            throw new ReflectionException("读取属性 '" + prop.getName() + "' 失败", e);
        }
    }

    @Override
    public void set(PropertyTokenizer prop, Object value) {
        try {
            reflector.getSetInvoker(prop.getName()).invoke(object, new Object[]{value});
        } catch (Exception e) {
            throw new ReflectionException("写入属性 '" + prop.getName() + "' 失败，值=" + value, e);
        }
    }

    /** 支持 list[0] / array[0] / map[key] 三种索引访问 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveIndexedValue(Object collection, String index) {
        if (collection == null) {
            return null;
        }
        if (collection instanceof List<?> list) {
            return list.get(Integer.parseInt(index));
        }
        if (collection instanceof java.util.Map map) {
            return map.get(index);
        }
        if (collection.getClass().isArray()) {
            return java.lang.reflect.Array.get(collection, Integer.parseInt(index));
        }
        throw new ReflectionException("类型 " + collection.getClass() + " 不支持索引访问 [" + index + "]");
    }

    @Override
    public boolean hasGetter(String name) {
        // 支持级联判断：a.b.c 需要逐层都可读
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (!prop.hasNext()) {
            return reflector.hasGetter(prop.getName());
        }
        if (!reflector.hasGetter(prop.getName())) {
            return false;
        }
        // 级联属性靠 MetaClass 做静态类型推导（不需要真实对象）
        MetaClass metaClass = MetaClass.forClass(reflector.getGetterType(prop.getName()), reflectorFactory);
        return metaClass.hasGetter(prop.getChildren());
    }

    @Override
    public boolean hasSetter(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (!prop.hasNext()) {
            return reflector.hasSetter(prop.getName());
        }
        if (!reflector.hasSetter(prop.getName())) {
            return false;
        }
        MetaClass metaClass = MetaClass.forClass(reflector.getSetterType(prop.getName()), reflectorFactory);
        return metaClass.hasSetter(prop.getChildren());
    }

    @Override
    public Class<?> getGetterType(String name) {
        return reflector.getGetterType(new PropertyTokenizer(name).getName());
    }

    @Override
    public Class<?> getSetterType(String name) {
        return reflector.getSetterType(new PropertyTokenizer(name).getName());
    }

    @Override
    public String findProperty(String name, boolean useCamelCaseMapping) {
        if (useCamelCaseMapping) {
            // 列名 user_name -> 去掉下划线后按忽略大小写匹配到 userName
            name = name.replace("_", "");
        }
        return reflector.findPropertyName(name);
    }

    @Override
    public Object getOriginalObject() {
        return object;
    }
}
