package com.learn.mybatismini.mapping;

import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.executor.keygen.KeyGenerator;
import com.learn.mybatismini.executor.keygen.NoKeyGenerator;
import com.learn.mybatismini.session.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 映射语句 —— <b>一个 &lt;select&gt;/&lt;insert&gt;/&lt;update&gt;/&lt;delete&gt; 标签
 * 在内存中的完整化身</b>。（对应 MyBatis 的 {@code MappedStatement}）
 *
 * <h2>这是全框架最核心的数据结构，务必牢记它装了什么</h2>
 * <pre>
 *   id               statementId，如 com.xxx.UserMapper.selectById（全局唯一）
 *   sqlCommandType   SELECT / INSERT / UPDATE / DELETE
 *   sqlSource        怎么产出 SQL（静态还是动态）
 *   resultMaps       结果怎么映射成对象
 *   parameterType    入参类型
 *   statementType    用 PreparedStatement 还是 Statement
 *   useCache         这条语句是否查二级缓存
 *   flushCacheRequired 执行后是否清空二级缓存
 *   cache            所属 namespace 的二级缓存实例（同一 namespace 共享一个）
 *   keyGenerator     主键生成/回填策略
 * </pre>
 *
 * <h3>生命周期与线程安全</h3>
 * MappedStatement 在<b>启动解析阶段</b>创建，之后<b>只读</b>，
 * 全局唯一一份存放在 {@code Configuration.mappedStatements} 这个 Map 里，
 * 被所有线程、所有 SqlSession 共享。
 * <p>正因为它只读，MyBatis 才能做到 "SqlSessionFactory 线程安全、可单例"。
 * 每次调用产生的可变状态（BoundSql、参数、结果）都在方法栈上，不写回 MappedStatement。
 *
 * <h3>建造者模式（Builder Pattern）</h3>
 * 本类字段多达 10 个且大部分可选，用构造器会变成"参数地狱"。
 * 因此提供 {@link Builder} 链式设置：
 * <pre>
 *   new MappedStatement.Builder(config, id, sqlSource, SqlCommandType.SELECT)
 *       .resultMaps(list).useCache(true).build();
 * </pre>
 * 同时把字段设为 final + 无 public setter，保证构建完成后<b>不可变</b>（线程安全的前提）。
 * 真实 MyBatis 源码用的就是这个写法。
 *
 * @author mybatis-source-learn
 */
public class MappedStatement {

    /** 全局配置引用（Executor / Handler 需要通过它拿注册表） */
    private final Configuration configuration;

    /** statementId = namespace + "." + id */
    private final String id;

    /** SQL 类型 */
    private final SqlCommandType sqlCommandType;

    /** SQL 来源（静态 / 动态） */
    private final SqlSource sqlSource;

    /** 结果映射（本简化版只用第一个；真实 MyBatis 支持多结果集） */
    private final List<ResultMap> resultMaps;

    /** 入参类型（可为 null，运行时按实际值推断） */
    private final Class<?> parameterType;

    /** Statement 类型 */
    private final StatementType statementType;

    /** 是否使用二级缓存（select 默认 true，其它默认 false） */
    private final boolean useCache;

    /** 执行后是否清空二级缓存（update 类默认 true） */
    private final boolean flushCacheRequired;

    /** 所属 namespace 的二级缓存（未配 <cache/> 时为 null） */
    private final Cache cache;

    /** 主键生成器 */
    private final KeyGenerator keyGenerator;

    /** 自增主键要回填到入参对象的哪个属性 */
    private final String keyProperty;

    /** 来源文件/接口，报错时提示用 */
    private final String resource;

    private MappedStatement(Builder builder) {
        this.configuration = builder.configuration;
        this.id = builder.id;
        this.sqlCommandType = builder.sqlCommandType;
        this.sqlSource = builder.sqlSource;
        this.resultMaps = Collections.unmodifiableList(new ArrayList<>(builder.resultMaps));
        this.parameterType = builder.parameterType;
        this.statementType = builder.statementType;
        this.useCache = builder.useCache;
        this.flushCacheRequired = builder.flushCacheRequired;
        this.cache = builder.cache;
        this.keyGenerator = builder.keyGenerator;
        this.keyProperty = builder.keyProperty;
        this.resource = builder.resource;
    }

    /**
     * 结合入参产出可执行 SQL。这是 MappedStatement 对外最重要的行为。
     */
    public BoundSql getBoundSql(Object parameterObject) {
        BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
        if (boundSql == null) {
            throw new IllegalStateException("语句 " + id + " 未能生成有效 SQL");
        }
        return boundSql;
    }

    // ------------------- getter（全部只读，无 setter） -------------------

    public Configuration getConfiguration() {
        return configuration;
    }

    public String getId() {
        return id;
    }

    public SqlCommandType getSqlCommandType() {
        return sqlCommandType;
    }

    public SqlSource getSqlSource() {
        return sqlSource;
    }

    public List<ResultMap> getResultMaps() {
        return resultMaps;
    }

    /** 便捷方法：取第一个（也是唯一的）ResultMap */
    public ResultMap getResultMap() {
        return resultMaps.isEmpty() ? null : resultMaps.get(0);
    }

    public Class<?> getParameterType() {
        return parameterType;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public boolean isFlushCacheRequired() {
        return flushCacheRequired;
    }

    public Cache getCache() {
        return cache;
    }

    public boolean hasCache() {
        return cache != null;
    }

    public KeyGenerator getKeyGenerator() {
        return keyGenerator;
    }

    public String getKeyProperty() {
        return keyProperty;
    }

    public String getResource() {
        return resource;
    }

    @Override
    public String toString() {
        return "MappedStatement{id='" + id + "', type=" + sqlCommandType
                + ", sqlSource=" + sqlSource.getClass().getSimpleName()
                + ", useCache=" + useCache + '}';
    }

    // ==================================================================
    // 建造者
    // ==================================================================

    /**
     * MappedStatement 的建造者。
     * <p>必填项通过构造器强制传入，可选项通过链式方法设置，并给出合理默认值。
     */
    public static class Builder {

        private final Configuration configuration;
        private final String id;
        private final SqlSource sqlSource;
        private final SqlCommandType sqlCommandType;

        private List<ResultMap> resultMaps = new ArrayList<>();
        private Class<?> parameterType;
        private StatementType statementType = StatementType.PREPARED;
        private boolean useCache;
        private boolean flushCacheRequired;
        private Cache cache;
        private KeyGenerator keyGenerator = new NoKeyGenerator();
        private String keyProperty;
        private String resource;

        public Builder(Configuration configuration, String id, SqlSource sqlSource,
                       SqlCommandType sqlCommandType) {
            this.configuration = configuration;
            this.id = id;
            this.sqlSource = sqlSource;
            this.sqlCommandType = sqlCommandType;
            // ★ 默认值规则（与真实 MyBatis 一致）：
            //   查询 -> 默认走缓存、不清缓存
            //   写操作 -> 默认不走缓存、执行后清缓存
            this.useCache = sqlCommandType == SqlCommandType.SELECT;
            this.flushCacheRequired = sqlCommandType != SqlCommandType.SELECT;
        }

        public Builder resultMaps(List<ResultMap> resultMaps) {
            if (resultMaps != null) {
                this.resultMaps = resultMaps;
            }
            return this;
        }

        public Builder resultMap(ResultMap resultMap) {
            if (resultMap != null) {
                this.resultMaps = new ArrayList<>(List.of(resultMap));
            }
            return this;
        }

        public Builder parameterType(Class<?> parameterType) {
            this.parameterType = parameterType;
            return this;
        }

        public Builder statementType(StatementType statementType) {
            if (statementType != null) {
                this.statementType = statementType;
            }
            return this;
        }

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public Builder flushCacheRequired(boolean flushCacheRequired) {
            this.flushCacheRequired = flushCacheRequired;
            return this;
        }

        public Builder cache(Cache cache) {
            this.cache = cache;
            return this;
        }

        public Builder keyGenerator(KeyGenerator keyGenerator) {
            if (keyGenerator != null) {
                this.keyGenerator = keyGenerator;
            }
            return this;
        }

        public Builder keyProperty(String keyProperty) {
            this.keyProperty = keyProperty;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public MappedStatement build() {
            if (id == null || sqlSource == null) {
                throw new IllegalStateException("MappedStatement 的 id 与 sqlSource 不能为空");
            }
            return new MappedStatement(this);
        }
    }
}
