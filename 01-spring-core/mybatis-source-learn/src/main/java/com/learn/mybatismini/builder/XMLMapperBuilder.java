package com.learn.mybatismini.builder;

import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.cache.PerpetualCache;
import com.learn.mybatismini.cache.decorators.FifoCache;
import com.learn.mybatismini.cache.decorators.LruCache;
import com.learn.mybatismini.executor.keygen.Jdbc3KeyGenerator;
import com.learn.mybatismini.executor.keygen.KeyGenerator;
import com.learn.mybatismini.executor.keygen.NoKeyGenerator;
import com.learn.mybatismini.io.Resources;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.ResultMap;
import com.learn.mybatismini.mapping.ResultMapping;
import com.learn.mybatismini.mapping.SqlCommandType;
import com.learn.mybatismini.mapping.SqlSource;
import com.learn.mybatismini.scripting.DynamicContext;
import com.learn.mybatismini.scripting.DynamicSqlSource;
import com.learn.mybatismini.scripting.RawSqlSource;
import com.learn.mybatismini.scripting.SqlNode;
import com.learn.mybatismini.scripting.XMLScriptBuilder;
import com.learn.mybatismini.session.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper XML 解析器（对应 MyBatis 的 {@code XMLMapperBuilder}）。
 *
 * <h2>把一个 Mapper.xml 解析成若干 MappedStatement</h2>
 * <pre>
 *   parse()
 *     ├─ parseCache()        有 <cache/> 就建一个 namespace 二级缓存（装饰器链）
 *     ├─ parseResultMaps()   解析 <resultMap id type>…</resultMap>
 *     ├─ parseSqlFragments() 解析 <sql id> 片段（供 <include> 复用）
 *     └─ parseStatements()   逐个 <select>/<insert>/<update>/<delete>
 *            │  用 XMLScriptBuilder 把标签树翻译成 SqlNode 树
 *            │  静态 → RawSqlSource；动态 → DynamicSqlSource
 *            ▼  注册成 MappedStatement（statementId = namespace.id）
 * </pre>
 *
 * <p>最后还会把 namespace（接口全限定名）注册进 MapperRegistry，
 * 这样 {@code sqlSession.getMapper(XxxMapper.class)} 才能拿到代理。
 */
public class XMLMapperBuilder extends BaseBuilder {

    private final Element root;

    private final String resource;

    private final String namespace;

    /** 本 namespace 的二级缓存（无 {@code <cache/>} 时为 null） */
    private Cache currentCache;

    /** sql 片段：shortId -> 文本 */
    private final Map<String, String> sqlFragments = new HashMap<>();

    public XMLMapperBuilder(Configuration configuration, InputStream inputStream, String resource) {
        super(configuration);
        this.resource = resource;
        this.root = parse(inputStream);
        this.namespace = root.getAttribute("namespace");
        if (namespace == null || namespace.isEmpty()) {
            throw new com.learn.mybatismini.exceptions.PersistenceException("Mapper 必须配置 namespace：" + resource);
        }
    }

    public void parse() {
        parseCache();
        parseResultMaps();
        parseSqlFragments();
        parseStatements();
        // 把 namespace（接口）注册进 MapperRegistry，使 getMapper 可用
        try {
            Class<?> mapperClass = Resources.classForName(namespace);
            configuration.addMapper(mapperClass);
        } catch (Exception ignored) {
            // namespace 不是合法类（纯 XML Mapper），跳过注册
        }
    }

    // ==================================================================
    // 解析各段
    // ==================================================================

    private void parseCache() {
        Element cacheEl = findChild(root, "cache");
        if (cacheEl == null) {
            return;
        }
        String eviction = cacheEl.getAttribute("eviction");
        if (eviction == null || eviction.isEmpty()) {
            eviction = "LRU";
        }
        int size = parseInt(cacheEl.getAttribute("size"), 1024);
        Cache cache = new PerpetualCache(namespace);
        if ("FIFO".equalsIgnoreCase(eviction)) {
            cache = new FifoCache(cache, size);
        } else {
            // 默认 LRU
            cache = new LruCache(cache, size);
        }
        configuration.addCache(cache);
        this.currentCache = cache;
    }

    private void parseResultMaps() {
        for (Element rmEl : childElements(root, "resultMap")) {
            String id = rmEl.getAttribute("id");
            String typeStr = rmEl.getAttribute("type");
            Class<?> type = resolveClass(typeStr);
            List<ResultMapping> mappings = new ArrayList<>();
            for (Element colEl : childElements(rmEl)) {
                String tag = colEl.getTagName();
                if ("id".equals(tag) || "result".equals(tag)) {
                    String column = colEl.getAttribute("column");
                    String property = colEl.getAttribute("property");
                    mappings.add(new ResultMapping(column, property, "id".equals(tag)));
                }
            }
            configuration.addResultMap(new ResultMap(namespace + "." + id, type, mappings));
        }
    }

    private void parseSqlFragments() {
        for (Element sqlEl : childElements(root, "sql")) {
            String id = sqlEl.getAttribute("id");
            String text = sqlEl.getTextContent();
            sqlFragments.put(id, text);
            sqlFragments.put(namespace + "." + id, text);
        }
    }

    private void parseStatements() {
        for (Element stmtEl : childElements(root, "select", "insert", "update", "delete")) {
            parseStatement(stmtEl);
        }
    }

    private void parseStatement(Element stmtEl) {
        String id = stmtEl.getAttribute("id");
        String statementId = namespace + "." + id;
        SqlCommandType type = SqlCommandType.valueOf(stmtEl.getTagName().toUpperCase());

        Class<?> parameterType = resolveClass(stmtEl.getAttribute("parameterType"));

        // 结果映射：resultMap 优先，否则用 resultType 生成隐式 ResultMap
        ResultMap resultMap = null;
        String resultMapAttr = stmtEl.getAttribute("resultMap");
        String resultTypeAttr = stmtEl.getAttribute("resultType");
        if (resultMapAttr != null && !resultMapAttr.isEmpty()) {
            resultMap = configuration.getResultMap(resultMapAttr);
            if (resultMap == null) {
                resultMap = configuration.getResultMap(namespace + "." + resultMapAttr);
            }
        } else if (resultTypeAttr != null && !resultTypeAttr.isEmpty()) {
            resultMap = ResultMap.ofType(statementId, resolveClass(resultTypeAttr));
        }

        // SqlSource：动态树 -> 静态 or 动态 SqlSource
        XMLScriptBuilder scriptBuilder = new XMLScriptBuilder(configuration, stmtEl, parameterType, sqlFragments, namespace);
        SqlNode rootNode = scriptBuilder.parseScriptNode();
        SqlSource sqlSource;
        if (scriptBuilder.isDynamic()) {
            sqlSource = new DynamicSqlSource(configuration, rootNode);
        } else {
            DynamicContext ctx = new DynamicContext(configuration, null);
            rootNode.apply(ctx);
            sqlSource = new RawSqlSource(configuration, ctx.getSql(), parameterType);
        }

        // 主键生成器
        KeyGenerator keyGenerator = new NoKeyGenerator();
        String keyProperty = null;
        if (type == SqlCommandType.INSERT || type == SqlCommandType.UPDATE) {
            if ("true".equalsIgnoreCase(stmtEl.getAttribute("useGeneratedKeys"))) {
                keyGenerator = new Jdbc3KeyGenerator();
                keyProperty = stmtEl.getAttribute("keyProperty");
            }
        }

        MappedStatement.Builder builder = new MappedStatement.Builder(configuration, statementId, sqlSource, type)
                .resource(resource)
                .keyGenerator(keyGenerator);
        if (keyProperty != null && !keyProperty.isEmpty()) {
            builder.keyProperty(keyProperty);
        }
        if (resultMap != null) {
            builder.resultMap(resultMap);
        }
        if (currentCache != null) {
            builder.cache(currentCache);
        }
        // useCache / flushCache 显式覆盖
        String useCache = stmtEl.getAttribute("useCache");
        if (useCache != null && !useCache.isEmpty()) {
            builder.useCache(Boolean.parseBoolean(useCache));
        }
        String flushCache = stmtEl.getAttribute("flushCache");
        if (flushCache != null && !flushCache.isEmpty()) {
            builder.flushCacheRequired(Boolean.parseBoolean(flushCache));
        }

        configuration.addMappedStatement(builder.build());
    }

    // ==================================================================
    // DOM 小工具
    // ==================================================================

    private Element parse(InputStream inputStream) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new com.learn.mybatismini.exceptions.PersistenceException("解析 Mapper XML 失败：" + resource, e);
        }
    }

    private Element parse(Reader reader) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new com.learn.mybatismini.exceptions.PersistenceException("解析 Mapper XML 失败：" + resource, e);
        }
    }

    private static List<Element> childElements(Element parent, String... tagNames) {
        List<Element> list = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                for (String tag : tagNames) {
                    if (tag.equals(e.getTagName())) {
                        list.add(e);
                        break;
                    }
                }
            }
        }
        return list;
    }

    private static Element findChild(Element parent, String tagName) {
        for (Element child : childElements(parent, tagName)) {
            return child;
        }
        return null;
    }

    private static int parseInt(String value, int def) {
        if (value == null || value.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
