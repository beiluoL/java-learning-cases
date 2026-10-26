package com.learn.mybatismini.reflection.invoker;

/**
 * 属性访问器抽象（对应 MyBatis 的 {@code Invoker}）。
 *
 * <h3>为什么要抽象出 Invoker？</h3>
 * 给一个对象的属性赋值，可能有两条路：
 * <ol>
 *   <li>调用 setter 方法（标准 JavaBean）—— {@link MethodInvoker}</li>
 *   <li>直接反射写字段（没有 setter 时）—— {@link SetFieldInvoker}</li>
 * </ol>
 * 上层（MetaObject / ResultSetHandler）不该关心用哪条路。于是定义统一接口 Invoker，
 * 由 Reflector 在解析类结构时决定用哪个实现放进 Map。
 *
 * <p>这是<b>策略模式</b>：同一个"设置属性"的行为有多种实现策略，运行时按情况选择。
 * 好处是 ResultSetHandler 里那句 {@code metaObject.setValue(prop, value)}
 * 无需任何 if-else 分支。
 *
 * @author mybatis-source-learn
 */
public interface Invoker {

    /**
     * 执行访问。
     *
     * @param target 目标对象
     * @param args   参数；getter 传 null，setter 传 new Object[]{value}
     * @return getter 返回属性值；setter 返回 null
     */
    Object invoke(Object target, Object[] args) throws Exception;

    /**
     * 该访问器操作的类型。
     * <ul>
     *   <li>getter：返回值类型</li>
     *   <li>setter：入参类型</li>
     * </ul>
     * ResultSetHandler 需要它来挑选合适的 TypeHandler。
     */
    Class<?> getType();
}
