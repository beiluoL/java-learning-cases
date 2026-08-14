package com.learn.mybatismini.builder;

import com.learn.mybatismini.annotations.CacheNamespace;
import com.learn.mybatismini.annotations.Delete;
import com.learn.mybatismini.annotations.Insert;
import com.learn.mybatismini.annotations.Options;
import com.learn.mybatismini.annotations.Select;
import com.learn.mybatismini.annotations.Update;
import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.cache.PerpetualCache;
import com.learn.mybatismini.cache.decorators.FifoCache;
import com.learn.mybatismini.cache.decorators.LruCache;
import com.learn.mybatismini.executor.keygen.Jdbc3KeyGenerator;
import com.learn.mybatismini.executor.keygen.KeyGenerator;
import com.learn.mybatismini.executor.keygen.NoKeyGenerator;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.ResultMap;
import com.learn.mybatismini.mapping.SqlCommandType;
import com.learn.mybatismini.mapping.SqlSource;
import com.learn.mybatismini.scripting.DynamicSqlSource;
import com.learn.mybatismini.scripting.MixedSqlNode;
import com.learn.mybatismini.scripting.RawSqlSource;
import com.learn.mybatismini.scripting.TextSqlNode;
import com.learn.mybatismini.session.Configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

/**
 * 注解式 Mapper 解析器（对应 MyBatis 的 {@code MapperAnnotationBuilder}）。
 *
 * <h3>和 XML 解析殊途同归：注解 SQL 最终也是 MappedStatement</h3>
 * <pre>
 *   &#64;Select("SELECT ... FROM t_user WHERE id = #{id}")
 *   User selectById(Long id);
 *        │  MapperAnnotationBuilder 读注解
 *        ▼
 *   MappedStatement(id = "XxxMapper.selectById", sqlSource = RawSqlSource, ...)
 * </pre>
 *
 * <p>注解方式不支持 {@code <if> / <foreach>} 这类动态标签（那是 XML 的强项），
 * 但能用 {@code ${}} 做字符串替换、{@code #{}} 做占位符，并靠 {@code @Options}
 * 补充 {@code useGeneratedKeys} / {@code useCache} 等开关。适合简单 SQL。
 */
public class MapperAnnotationBuilder extends BaseBuilder {

    private final Class<?> mapperInterface;

    private final String namespace;

    private Cache currentCache;

    public MapperAnnotationBuilder(Configuration configuration, Class<?> mapperInterface) {
        super(configuration);
        this.mapperInterface = mapperInterface;
        this.namespace = mapperInterface.getName();
    }

    public void parse() {
        parseCache();
        for (Method method : mapperInterface.getDeclaredMethods()) {
            parseStatement(method);
        }
        // 确保接口被注册，使 getMapper 可用
        configuration.addMapper(mapperInterface);
    }

    private void parseCache() {
        CacheNamespace cacheNs = mapperInterface.getAnnotation(CacheNamespace.class);
        if (cacheNs == null) {
            return;
        }
        String eviction = cacheNs.eviction();
        if (eviction == null || eviction.isEmpty()) {
            eviction = "LRU";
        }
        Cache cache = new PerpetualCache(namespace);
        if ("FIFO".equalsIgnoreCase(eviction)) {
            cache = new FifoCache(cache, cacheNs.size());
        } else {
            cache = new LruCache(cache, cacheNs.size());
        }
        configuration.addCache(cache);
        this.currentCache = cache;
    }

    private void parseStatement(Method method) {
        // 找出方法上带 SQL 的注解（@Select/@Insert/@Update/@Delete）
        Select selectAnno = method.getAnnotation(Select.class);
        Insert insertAnno = method.getAnnotation(Insert.class);
        Update updateAnno = method.getAnnotation(Update.class);
        Delete deleteAnno = method.getAnnotation(Delete.class);

        Annotation sqlAnno = selectAnno != null ? selectAnno
                : insertAnno != null ? insertAnno
                : updateAnno != null ? updateAnno
                : deleteAnno;
        if (sqlAnno == null) {
            return; // 非 SQL 方法（如默认方法），跳过
        }

        SqlCommandType type = selectAnno != null ? SqlCommandType.SELECT
                : insertAnno != null ? SqlCommandType.INSERT
                : updateAnno != null ? SqlCommandType.UPDATE
                : SqlCommandType.DELETE;

        String sql = ((String) invokeValue(sqlAnno)).trim();
        String statementId = namespace + "." + method.getName();

        // 查询：按返回类型（集合则取元素类型）生成隐式 ResultMap
        ResultMap resultMap = null;
        if (type == SqlCommandType.SELECT) {
            Class<?> ret = method.getReturnType();
            Class<?> elementType = (Collection.class.isAssignableFrom(ret) || ret.isArray())
                    ? getGenericReturnType(method) : ret;
            resultMap = ResultMap.ofType(statementId, elementType);
        }

        // SqlSource：含 ${} 视为动态，否则静态
        SqlSource sqlSource;
        if (sql.contains("${")) {
            sqlSource = new DynamicSqlSource(configuration, new MixedSqlNode(List.of(new TextSqlNode(sql))));
        } else {
            sqlSource = new RawSqlSource(configuration, sql, null);
        }

        // @Options 补充开关
        KeyGenerator keyGenerator = new NoKeyGenerator();
        String keyProperty = null;
        boolean useCache = true;
        boolean flushCache = false;
        Options opts = method.getAnnotation(Options.class);
        if (opts != null) {
            if (opts.useGeneratedKeys()) {
                keyGenerator = new Jdbc3KeyGenerator();
                keyProperty = opts.keyProperty();
            }
            useCache = opts.useCache();
            flushCache = opts.flushCache();
        }

        MappedStatement.Builder builder = new MappedStatement.Builder(configuration, statementId, sqlSource, type)
                .resource(mapperInterface.getName())
                .keyGenerator(keyGenerator)
                .useCache(useCache);
        if (flushCache) {
            builder.flushCacheRequired(true);
        }
        if (keyProperty != null && !keyProperty.isEmpty()) {
            builder.keyProperty(keyProperty);
        }
        if (resultMap != null) {
            builder.resultMap(resultMap);
        }
        if (currentCache != null) {
            builder.cache(currentCache);
        }
        if (type != SqlCommandType.SELECT) {
            // 写操作默认不走二级缓存
            builder.useCache(false);
        }
        configuration.addMappedStatement(builder.build());
    }

    private static Object invokeValue(Annotation anno) {
        try {
            return anno.annotationType().getMethod("value").invoke(anno);
        } catch (Exception e) {
            throw new com.learn.mybatismini.exceptions.PersistenceException("读取注解 SQL 失败：" + anno, e);
        }
    }

    private static Class<?> getGenericReturnType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        return Object.class;
    }
}
