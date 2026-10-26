package com.learn.mybatismini.session;

import com.learn.mybatismini.binding.MapperRegistry;
import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.mapping.Environment;
import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.executor.BaseExecutor;
import com.learn.mybatismini.executor.CachingExecutor;
import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.executor.ReuseExecutor;
import com.learn.mybatismini.executor.SimpleExecutor;
import com.learn.mybatismini.executor.keygen.NoKeyGenerator;
import com.learn.mybatismini.executor.statement.DefaultParameterHandler;
import com.learn.mybatismini.executor.statement.DefaultResultSetHandler;
import com.learn.mybatismini.executor.statement.ParameterHandler;
import com.learn.mybatismini.executor.statement.PreparedStatementHandler;
import com.learn.mybatismini.executor.statement.ResultSetHandler;
import com.learn.mybatismini.executor.statement.SimpleStatementHandler;
import com.learn.mybatismini.executor.statement.StatementHandler;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.ResultMap;
import com.learn.mybatismini.mapping.StatementType;
import com.learn.mybatismini.plugin.Interceptor;
import com.learn.mybatismini.plugin.InterceptorChain;
import com.learn.mybatismini.reflection.MetaObject;
import com.learn.mybatismini.reflection.ObjectFactory;
import com.learn.mybatismini.reflection.ReflectorFactory;
import com.learn.mybatismini.transaction.Transaction;
import com.learn.mybatismini.type.TypeAliasRegistry;
import com.learn.mybatismini.type.TypeHandlerRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局配置（对应 MyBatis 的 {@code Configuration}）。
 *
 * <h2>整个框架的"中央枢纽"—— 所有注册表、所有组件都在这里</h2>
 * <pre>
 *   类型系统    typeAliasRegistry / typeHandlerRegistry / reflectorFactory / objectFactory
 *   映射仓库    mappedStatements / caches / resultMaps
 *   Mapper      mapperRegistry（接口 -> 代理工厂）
 *   插件链      interceptorChain（Executor/Handler 创建时层层套代理）
 *   运行环境    environment（事务工厂 + 数据源）
 *   行为开关    cacheEnabled / mapUnderscoreToCamelCase / defaultExecutorType / logSqlEnabled
 * </pre>
 *
 * <h3>为什么它"线程安全、可单例"？</h3>
 * 因为解析期产出的 MappedStatement / Cache / ResultMap 全部<b>不可变</b>（final 字段），
 * 运行期的可变状态（BoundSql、参数、结果、一级缓存）都在方法栈或各 SqlSession 自己的
 * Executor 里，不写回 Configuration。所以一个 Configuration + SqlSessionFactory 能被
 * 所有线程共享。
 *
 * @author mybatis-source-learn
 */
public class Configuration {

    // ------------------- 行为开关（settings） -------------------
    private boolean cacheEnabled = true;
    private boolean mapUnderscoreToCamelCase;
    private ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
    private boolean logSqlEnabled = true;

    // ------------------- 类型系统（注册表） -------------------
    private final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
    private final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();
    private final ReflectorFactory reflectorFactory = new ReflectorFactory();
    private final ObjectFactory objectFactory = new ObjectFactory();

    // ------------------- 映射仓库（解析期产物） -------------------
    /** statementId -> MappedStatement */
    private final StrictMap<MappedStatement> mappedStatements = new StrictMap<>("Mapped Statements");
    /** namespace -> 二级缓存实例 */
    private final StrictMap<Cache> caches = new StrictMap<>("Caches");
    /** resultMapId -> ResultMap */
    private final StrictMap<ResultMap> resultMaps = new StrictMap<>("Result Maps");

    // ------------------- Mapper 注册中心 -------------------
    private final MapperRegistry mapperRegistry;

    // ------------------- 插件责任链 -------------------
    private final InterceptorChain interceptorChain = new InterceptorChain();

    // ------------------- 运行环境 -------------------
    private Environment environment;

    public Configuration() {
        this.mapperRegistry = new MapperRegistry(this);
        // 让 MiniLog 的开关与配置保持一致
        MiniLog.setSqlLogEnabled(logSqlEnabled);
    }

    // ==================================================================
    // 工厂方法：创建核心组件（并统一套上插件代理）
    // ==================================================================

    /**
     * 创建执行器：Simple/Reuse + 按需套 CachingExecutor + 套插件。
     */
    public Executor newExecutor(Transaction transaction) {
        return newExecutor(transaction, defaultExecutorType);
    }

    public Executor newExecutor(Transaction transaction, ExecutorType type) {
        Executor executor;
        if (type == ExecutorType.REUSE) {
            executor = new ReuseExecutor(this, transaction);
        } else {
            executor = new SimpleExecutor(this, transaction);
        }
        // ★ 二级缓存：开关打开时，用装饰器包一层（不改动底层执行器）
        if (cacheEnabled) {
            executor = new CachingExecutor(executor);
        }
        // ★ 插件：对 Executor 层层套代理（PageHelper 之类就是在这里生效）
        executor = (Executor) interceptorChain.pluginAll(executor);
        return executor;
    }

    /**
     * 创建语句处理器（PreparedStatement / Statement），并套插件代理。
     */
    public StatementHandler newStatementHandler(Executor executor, MappedStatement ms,
                                                Object parameter, BoundSql boundSql) {
        StatementHandler handler;
        if (ms.getStatementType() == StatementType.STATEMENT) {
            handler = new SimpleStatementHandler(this, executor, ms, parameter, boundSql);
        } else {
            handler = new PreparedStatementHandler(this, executor, ms, parameter, boundSql);
        }
        return (StatementHandler) interceptorChain.pluginAll(handler);
    }

    public ParameterHandler newParameterHandler(MappedStatement ms, Object parameter, BoundSql boundSql) {
        ParameterHandler handler = new DefaultParameterHandler(this, ms, parameter, boundSql);
        return (ParameterHandler) interceptorChain.pluginAll(handler);
    }

    public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement ms, BoundSql boundSql) {
        ResultSetHandler handler = new DefaultResultSetHandler(executor, ms, this, boundSql);
        return (ResultSetHandler) interceptorChain.pluginAll(handler);
    }

    // ==================================================================
    // 元对象 / 反射便捷入口
    // ==================================================================

    public MetaObject newMetaObject(Object object) {
        return MetaObject.forObject(object, objectFactory, reflectorFactory);
    }

    // ==================================================================
    // 注册表访问
    // ==================================================================

    public MapperRegistry getMapperRegistry() {
        return mapperRegistry;
    }

    public void addMapper(Class<?> type) {
        mapperRegistry.addMapper(type);
    }

    public boolean hasMapper(Class<?> type) {
        return mapperRegistry.hasMapper(type);
    }

    public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
        return mapperRegistry.getMapper(type, sqlSession);
    }

    public void addMappedStatement(MappedStatement ms) {
        mappedStatements.put(ms.getId(), ms);
    }

    public MappedStatement getMappedStatement(String id) {
        return mappedStatements.get(id);
    }

    /** 已注册的 MappedStatement 数量（便于演示时打印"解析了多少条 SQL"） */
    public int getMappedStatementCount() {
        return mappedStatements.size();
    }

    public void addCache(Cache cache) {
        caches.put(cache.getId(), cache);
    }

    public Cache getCache(String id) {
        return caches.get(id);
    }

    public void addResultMap(ResultMap resultMap) {
        resultMaps.put(resultMap.getId(), resultMap);
    }

    public ResultMap getResultMap(String id) {
        return resultMaps.get(id);
    }

    public TypeAliasRegistry getTypeAliasRegistry() {
        return typeAliasRegistry;
    }

    public TypeHandlerRegistry getTypeHandlerRegistry() {
        return typeHandlerRegistry;
    }

    public ReflectorFactory getReflectorFactory() {
        return reflectorFactory;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public InterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    public void addInterceptor(Interceptor interceptor) {
        interceptorChain.addInterceptor(interceptor);
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    // ==================================================================
    // settings 开关
    // ==================================================================

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public boolean isMapUnderscoreToCamelCase() {
        return mapUnderscoreToCamelCase;
    }

    public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
        this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
    }

    public ExecutorType getDefaultExecutorType() {
        return defaultExecutorType;
    }

    public void setDefaultExecutorType(ExecutorType defaultExecutorType) {
        this.defaultExecutorType = defaultExecutorType;
    }

    public boolean isLogSqlEnabled() {
        return logSqlEnabled;
    }

    public void setLogSqlEnabled(boolean logSqlEnabled) {
        this.logSqlEnabled = logSqlEnabled;
        MiniLog.setSqlLogEnabled(logSqlEnabled);
    }

    // ==================================================================
    // 严格 Map：缺失/重复都抛明确异常（避免静默出 bug）
    // ==================================================================

    /**
     * 一个会在"重复 key"和"缺失 key"时抛异常的 HashMap。
     * 让配置错误（重复 statementId、引用不存在的 resultMap）尽早暴露。
     */
    public static class StrictMap<V> extends HashMap<String, V> {

        private final String name;

        public StrictMap(String name) {
            this.name = name;
        }

        @Override
        public V put(String key, V value) {
            if (containsKey(key)) {
                throw new PersistenceException(name + " 已存在同名 key：" + key
                        + "（请检查 Mapper 里是否定义了重复的 id / resultMap id）");
            }
            return super.put(key, value);
        }

        @Override
        public V get(Object key) {
            V value = super.get(key);
            if (value == null) {
                throw new PersistenceException(name + " 中找不到 key：" + key);
            }
            return value;
        }
    }
}
