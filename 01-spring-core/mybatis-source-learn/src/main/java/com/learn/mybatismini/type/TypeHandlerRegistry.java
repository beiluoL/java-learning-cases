package com.learn.mybatismini.type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 类型处理器注册中心（对应 MyBatis 的 {@code TypeHandlerRegistry}）。
 *
 * <h2>它就是一张"Java 类型 -> TypeHandler"的路由表</h2>
 * <pre>
 *   Long.class      -> LongTypeHandler
 *   long.class      -> LongTypeHandler     （基本类型与包装类型都要注册！）
 *   String.class    -> StringTypeHandler
 *   BigDecimal.class-> BigDecimalTypeHandler
 *   ...
 *   兜底             -> ObjectTypeHandler
 * </pre>
 *
 * <h3>为什么基本类型和包装类型都要注册？</h3>
 * 因为反射拿到的类型可能是任一种：
 * <pre>
 *   private int age;      -> reflector.getSetterType("age") == int.class
 *   private Integer age;  -> reflector.getSetterType("age") == Integer.class
 * </pre>
 * 少注册一种就会 fallback 到 ObjectTypeHandler，虽然多半也能跑，
 * 但会丧失精确的 null 处理与类型转换 —— 属于"能跑但埋雷"的问题。
 *
 * <h3>它在一次 SQL 执行中被查询两次</h3>
 * <ol>
 *   <li>设参数时：DefaultParameterHandler 按 <b>入参属性的 Java 类型</b> 找 handler；</li>
 *   <li>取结果时：DefaultResultSetHandler 按 <b>实体属性的 Java 类型</b> 找 handler。</li>
 * </ol>
 *
 * <h3>如何扩展自己的 TypeHandler</h3>
 * <pre>
 *   configuration.getTypeHandlerRegistry().register(MyEnum.class, new MyEnumTypeHandler());
 * </pre>
 * 真实 MyBatis 还支持在 XML 里配 {@code <typeHandlers>} 批量注册，原理相同。
 *
 * @author mybatis-source-learn
 */
public class TypeHandlerRegistry {

    /** Java 类型 -> 处理器 */
    private final Map<Class<?>, TypeHandler<?>> typeHandlerMap = new HashMap<>();

    /** 兜底处理器 */
    private final TypeHandler<Object> unknownTypeHandler = new ObjectTypeHandler();

    public TypeHandlerRegistry() {
        // ---------- 整型 ----------
        register(Integer.class, new IntegerTypeHandler());
        register(int.class, new IntegerTypeHandler());
        register(Long.class, new LongTypeHandler());
        register(long.class, new LongTypeHandler());

        // ---------- 字符串 ----------
        register(String.class, new StringTypeHandler());

        // ---------- 布尔 ----------
        register(Boolean.class, new BooleanTypeHandler());
        register(boolean.class, new BooleanTypeHandler());

        // ---------- 浮点与精确小数 ----------
        register(Double.class, new DoubleTypeHandler());
        register(double.class, new DoubleTypeHandler());
        register(BigDecimal.class, new BigDecimalTypeHandler());

        // ---------- 时间 ----------
        register(java.util.Date.class, new DateTypeHandler());
        register(LocalDateTime.class, new LocalDateTimeTypeHandler());

        // ---------- 兜底 ----------
        register(Object.class, unknownTypeHandler);
    }

    /**
     * 注册（或覆盖）某个 Java 类型的处理器。
     */
    public <T> void register(Class<T> javaType, TypeHandler<? extends T> handler) {
        typeHandlerMap.put(javaType, handler);
    }

    /**
     * 按 Java 类型取处理器；没有精确匹配时逐级降级：
     * <ol>
     *   <li>精确匹配</li>
     *   <li>枚举 -> 按名字存字符串（用 StringTypeHandler 近似处理）</li>
     *   <li>父类匹配（如自定义的 Date 子类）</li>
     *   <li>兜底 ObjectTypeHandler</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
        if (type == null) {
            return (TypeHandler<T>) unknownTypeHandler;
        }
        TypeHandler<?> handler = typeHandlerMap.get(type);
        if (handler != null) {
            return (TypeHandler<T>) handler;
        }
        // 枚举：简化处理为按 name() 存字符串
        if (type.isEnum()) {
            return (TypeHandler<T>) typeHandlerMap.get(String.class);
        }
        // 沿父类链找一个能用的
        Class<?> superClass = type.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            TypeHandler<?> superHandler = typeHandlerMap.get(superClass);
            if (superHandler != null) {
                return (TypeHandler<T>) superHandler;
            }
            superClass = superClass.getSuperclass();
        }
        return (TypeHandler<T>) unknownTypeHandler;
    }

    /**
     * 按 Java 类型 + JDBC 类型取处理器。
     *
     * <p>本项目为教学简化版：JDBC 类型仅作为语义信息保留（例如便于日后按列类型精确选 handler），
     * 实际路由仍按 Java 类型进行，与单参版本逻辑一致；找不到时逐级降级到兜底处理器。
     * 真实 MyBatis 这里会进一步结合 {@code jdbcType} 在注册表里做二级检索。
     */
    public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
        return getTypeHandler(type);
    }

    /**
     * 是否为"简单类型"（有专属 TypeHandler 的类型）。
     *
     * <p>这个判断非常关键，ResultSetHandler 用它决定映射策略：
     * <pre>
     *   resultType=Long/String/Integer ->  简单类型，直接取第 1 列的值返回
     *   resultType=User                ->  复杂类型，需要逐列映射到属性
     * </pre>
     * 这就是 {@code SELECT COUNT(*)} 能直接返回 long 的原因。
     */
    public boolean hasTypeHandler(Class<?> type) {
        return type != null && typeHandlerMap.containsKey(type);
    }

    public TypeHandler<Object> getUnknownTypeHandler() {
        return unknownTypeHandler;
    }
}
