package com.learn.mybatismini.type;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.io.Resources;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 类型别名注册表（对应 MyBatis 的 {@code TypeAliasRegistry}）。
 *
 * <h3>解决什么问题？</h3>
 * 让你在 Mapper XML 里少写字：
 * <pre>
 *   resultType="com.learn.mybatismini.demo.entity.User"   繁琐
 *   resultType="User"                                     清爽（靠别名）
 *   resultType="int" / "long" / "map" / "string"          内置别名
 * </pre>
 *
 * <h3>实现要点</h3>
 * <ol>
 *   <li>内部就是一个 {@code Map<String, Class<?>>}；</li>
 *   <li><b>key 统一转小写</b>：所以 {@code resultType="USER"} 和 {@code "user"} 都能命中，
 *       这是 MyBatis 别名大小写不敏感的原因；</li>
 *   <li>{@link #resolveAlias(String)} 先查别名表，查不到就当成全限定类名去 Class.forName
 *       —— 这个"先别名后全名"的降级策略让两种写法都能工作。</li>
 * </ol>
 *
 * @author mybatis-source-learn
 */
public class TypeAliasRegistry {

    /** 别名（小写） -> Class */
    private final Map<String, Class<?>> typeAliases = new HashMap<>();

    public TypeAliasRegistry() {
        // ---------- 基本类型与包装类型 ----------
        registerAlias("byte", Byte.class);
        registerAlias("short", Short.class);
        registerAlias("int", Integer.class);
        registerAlias("integer", Integer.class);
        registerAlias("long", Long.class);
        registerAlias("float", Float.class);
        registerAlias("double", Double.class);
        registerAlias("boolean", Boolean.class);
        registerAlias("string", String.class);

        // ---------- 常用引用类型 ----------
        registerAlias("decimal", BigDecimal.class);
        registerAlias("bigdecimal", BigDecimal.class);
        registerAlias("date", Date.class);
        registerAlias("object", Object.class);

        // ---------- 集合类型 ----------
        registerAlias("map", Map.class);
        registerAlias("hashmap", HashMap.class);
        registerAlias("list", List.class);
        registerAlias("arraylist", ArrayList.class);

        // ---------- 事务/数据源类型别名（供 XMLConfigBuilder 解析 type 属性用） ----------
        registerAlias("JDBC", com.learn.mybatismini.transaction.JdbcTransactionFactory.class);
        registerAlias("UNPOOLED", com.learn.mybatismini.datasource.UnpooledDataSourceFactory.class);
        registerAlias("POOLED", com.learn.mybatismini.datasource.PooledDataSourceFactory.class);

        // ---------- 缓存回收策略别名 ----------
        registerAlias("PERPETUAL", com.learn.mybatismini.cache.PerpetualCache.class);
        registerAlias("LRU", com.learn.mybatismini.cache.decorators.LruCache.class);
        registerAlias("FIFO", com.learn.mybatismini.cache.decorators.FifoCache.class);
    }

    /**
     * 注册别名。key 统一小写，实现大小写不敏感。
     */
    public void registerAlias(String alias, Class<?> value) {
        if (alias == null) {
            throw new PersistenceException("别名不能为 null");
        }
        String key = alias.toLowerCase(Locale.ROOT);
        Class<?> exist = typeAliases.get(key);
        // 同名不同类，说明配置冲突，必须报错而不是静默覆盖
        if (exist != null && !exist.equals(value)) {
            throw new PersistenceException("别名 '" + alias + "' 已被 " + exist.getName()
                    + " 占用，无法再注册给 " + value.getName());
        }
        typeAliases.put(key, value);
    }

    /**
     * 按简单类名自动注册别名（如 User 类 -> 别名 "user"）。
     */
    public void registerAlias(Class<?> type) {
        registerAlias(type.getSimpleName(), type);
    }

    /**
     * 解析类型字符串 -> Class。
     *
     * <p>降级策略：别名表 -> 全限定类名。
     * 这就是 {@code resultType} 既能写 "User" 也能写全名的原因。
     */
    public Class<?> resolveAlias(String string) {
        if (string == null) {
            return null;
        }
        String key = string.trim().toLowerCase(Locale.ROOT);
        Class<?> value = typeAliases.get(key);
        if (value != null) {
            return value;
        }
        // 不是别名，当成全限定类名
        return Resources.classForName(string.trim());
    }
}
