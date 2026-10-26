package com.learn.mybatismini.mapping;

/**
 * JDBC Statement 类型（对应 MyBatis 的 {@code StatementType}）。
 *
 * <pre>
 *   STATEMENT  ->  Statement          直接拼字符串执行，不预编译（有注入风险，几乎不用）
 *   PREPARED   ->  PreparedStatement  预编译 + ? 占位符（<b>默认且推荐</b>）
 *   CALLABLE   ->  CallableStatement  调用存储过程
 * </pre>
 *
 * <h3>为什么 PreparedStatement 是默认值？</h3>
 * <ol>
 *   <li><b>防 SQL 注入</b>：参数走独立通道传输，不参与 SQL 语法解析；</li>
 *   <li><b>预编译缓存</b>：数据库对同一 SQL 模板只需解析一次执行计划，
 *       后续换参数复用，QPS 高时收益显著；</li>
 *   <li><b>类型安全</b>：setLong/setTimestamp 由驱动负责格式化，
 *       不用自己拼引号和日期格式。</li>
 * </ol>
 *
 * @author mybatis-source-learn
 */
public enum StatementType {

    /** 普通 Statement（不预编译） */
    STATEMENT,

    /** 预编译 Statement（默认） */
    PREPARED,

    /** 存储过程 */
    CALLABLE
}
