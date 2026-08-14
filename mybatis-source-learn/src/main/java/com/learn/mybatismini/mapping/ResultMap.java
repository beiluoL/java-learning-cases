package com.learn.mybatismini.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 结果映射规则集合（对应 MyBatis 的 {@code ResultMap}）。
 *
 * <h3>两种映射模式的区别（重要）</h3>
 * <pre>
 *   resultType="User"      -> 框架<b>自动</b>建一个"隐式 ResultMap"（mappings 为空），
 *                             运行时按 列名->属性名 的规则猜（可选下划线转驼峰）
 *   resultMap="userMap"    -> 使用你<b>显式</b>声明的映射，列名和属性名可以毫无关系
 * </pre>
 * DefaultResultSetHandler 拿到 ResultMap 后：
 * <ul>
 *   <li>mappings 非空 -> 按声明逐项映射（精确、可控）；</li>
 *   <li>mappings 为空 -> 走自动映射（省事、约定优于配置）。</li>
 * </ul>
 *
 * <p>真实 MyBatis 的 ResultMap 还支持 {@code <association>}（一对一）、
 * {@code <collection>}（一对多）、{@code discriminator}（鉴别器）等嵌套映射，
 * 本简化版聚焦单表映射，把主干讲透。
 *
 * @author mybatis-source-learn
 */
public class ResultMap {

    /** 唯一标识（namespace 内唯一，如 userResultMap） */
    private final String id;

    /** 映射的目标 Java 类型 */
    private final Class<?> type;

    /** 所有映射项；为空表示走"自动映射" */
    private final List<ResultMapping> resultMappings;

    public ResultMap(String id, Class<?> type, List<ResultMapping> resultMappings) {
        this.id = id;
        this.type = type;
        this.resultMappings = resultMappings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(resultMappings));
    }

    /**
     * 便捷构造：由 resultType 生成的"隐式 ResultMap"（无显式映射项）。
     */
    public static ResultMap ofType(String id, Class<?> type) {
        return new ResultMap(id, type, Collections.emptyList());
    }

    public String getId() {
        return id;
    }

    public Class<?> getType() {
        return type;
    }

    public List<ResultMapping> getResultMappings() {
        return resultMappings;
    }

    /** 是否有显式映射项（决定走"显式映射"还是"自动映射"） */
    public boolean hasExplicitMappings() {
        return !resultMappings.isEmpty();
    }

    @Override
    public String toString() {
        return "ResultMap{id='" + id + "', type=" + (type == null ? "null" : type.getSimpleName())
                + ", mappings=" + resultMappings.size() + '}';
    }
}
