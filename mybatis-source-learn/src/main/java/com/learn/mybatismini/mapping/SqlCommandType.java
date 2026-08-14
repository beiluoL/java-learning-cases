package com.learn.mybatismini.mapping;

/**
 * SQL 命令类型（对应 MyBatis 的 {@code SqlCommandType}）。
 *
 * <h3>它决定了执行路径的分叉</h3>
 * <pre>
 *   SELECT                     -> executor.query(...)   走查询链路（含缓存）
 *   INSERT / UPDATE / DELETE   -> executor.update(...)  走更新链路（清缓存）
 * </pre>
 * MapperMethod 拿到 MappedStatement 后第一件事就是看这个枚举，
 * 决定调 SqlSession 的哪个方法。
 *
 * @author mybatis-source-learn
 */
public enum SqlCommandType {

    /** 未知（占位） */
    UNKNOWN,

    /** 查询 */
    SELECT,

    /** 新增 */
    INSERT,

    /** 修改 */
    UPDATE,

    /** 删除 */
    DELETE;

    /** 是否属于"写操作"（会触发缓存清空） */
    public boolean isUpdateType() {
        return this == INSERT || this == UPDATE || this == DELETE;
    }
}
