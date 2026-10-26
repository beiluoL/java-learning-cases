package com.learn.mybatismini.builder;

import com.learn.mybatismini.datasource.DataSourceFactory;
import com.learn.mybatismini.mapping.Environment;
import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.io.Resources;
import com.learn.mybatismini.plugin.Interceptor;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.ExecutorType;
import com.learn.mybatismini.transaction.TransactionFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 全局配置解析器（对应 MyBatis 的 {@code XMLConfigBuilder}）。
 *
 * <h2>mini-mybatis-config.xml  →  Configuration 的总入口</h2>
 * <pre>
 *   XMLConfigBuilder.parse()
 *        │  逐个解析顶层节点
 *        ├─ settings        → 设置 Configuration 的开关
 *        ├─ typeAliases      → 注册类型别名
 *        ├─ plugins          → 实例化并加入 InterceptorChain
 *        ├─ environments     → 建 Environment（事务工厂 + 数据源）
 *        └─ mappers          → 逐个 XMLMapperBuilder / MapperAnnotationBuilder 注册 SQL
 *        ▼
 *   Configuration（可被 SqlSessionFactory 长期持有）
 * </pre>
 *
 * <p>本项目刻意<b>不</b>用任何 XML 校验（DTD/XSD），拿到什么节点就按名字取，
 * 这样初学者能一眼看穿"XML 标签 → Java 对象"的映射，而不是被 schema 噪音干扰。
 */
public class XMLConfigBuilder extends BaseBuilder {

    private final Element root;

    public XMLConfigBuilder(InputStream inputStream) {
        super(new Configuration());
        this.root = parse(inputStream);
    }

    public XMLConfigBuilder(Reader reader) {
        super(new Configuration());
        this.root = parse(reader);
    }

    public Configuration parse() {
        parseConfiguration(root);
        return configuration;
    }

    // ==================================================================
    // DOM 解析
    // ==================================================================

    private Element parse(InputStream inputStream) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setNamespaceAware(false);
            Document doc = dbf.newDocumentBuilder().parse(inputStream);
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new PersistenceException("解析 mybatis-config.xml 失败", e);
        }
    }

    private Element parse(Reader reader) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setNamespaceAware(false);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(reader));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new PersistenceException("解析 mybatis-config.xml 失败", e);
        }
    }

    private void parseConfiguration(Element root) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            switch (name) {
                case "settings":
                    parseSettings((Element) node);
                    break;
                case "typeAliases":
                    parseTypeAliases((Element) node);
                    break;
                case "plugins":
                    parsePlugins((Element) node);
                    break;
                case "environments":
                    parseEnvironments((Element) node);
                    break;
                case "mappers":
                    parseMappers((Element) node);
                    break;
                default:
                    // 其它节点（如 objectFactory / settings 之外的扩展）本简化版忽略
                    break;
            }
        }
    }

    private void parseSettings(Element settingsEl) {
        for (Element setting : childElements(settingsEl)) {
            String name = setting.getAttribute("name");
            String value = setting.getAttribute("value");
            switch (name) {
                case "cacheEnabled":
                    configuration.setCacheEnabled(Boolean.parseBoolean(value));
                    break;
                case "mapUnderscoreToCamelCase":
                    configuration.setMapUnderscoreToCamelCase(Boolean.parseBoolean(value));
                    break;
                case "defaultExecutorType":
                    configuration.setDefaultExecutorType(ExecutorType.valueOf(value.trim().toUpperCase()));
                    break;
                case "logSqlEnabled":
                    configuration.setLogSqlEnabled(Boolean.parseBoolean(value));
                    break;
                default:
                    // 未知 setting 忽略
                    break;
            }
        }
    }

    private void parseTypeAliases(Element aliasesEl) {
        for (Element aliasEl : childElements(aliasesEl)) {
            String alias = aliasEl.getAttribute("alias");
            String type = aliasEl.getAttribute("type");
            configuration.getTypeAliasRegistry().registerAlias(alias, Resources.classForName(type));
        }
    }

    private void parsePlugins(Element pluginsEl) {
        for (Element pluginEl : childElements(pluginsEl)) {
            String interceptorClass = pluginEl.getAttribute("interceptor");
            Interceptor interceptor = (Interceptor) instantiate(Resources.classForName(interceptorClass));
            Properties props = new Properties();
            for (Element propEl : childElements(pluginEl)) {
                props.setProperty(propEl.getAttribute("name"), propEl.getAttribute("value"));
            }
            interceptor.setProperties(props);
            configuration.addInterceptor(interceptor);
        }
    }

    private void parseEnvironments(Element environmentsEl) {
        String defaultId = environmentsEl.getAttribute("default");
        Map<String, Environment> envMap = new LinkedHashMap<>();
        for (Element envEl : childElements(environmentsEl)) {
            String id = envEl.getAttribute("id");
            Element txEl = findChild(envEl, "transactionManager");
            Element dsEl = findChild(envEl, "dataSource");
            if (txEl == null || dsEl == null) {
                throw new PersistenceException("environment[" + id + "] 必须同时配置 transactionManager 与 dataSource");
            }
            TransactionFactory tf = (TransactionFactory) instantiate(resolveClass(txEl.getAttribute("type")));
            DataSourceFactory dsf = (DataSourceFactory) instantiate(resolveClass(dsEl.getAttribute("type")));
            Properties dsProps = new Properties();
            for (Element propEl : childElements(dsEl)) {
                dsProps.setProperty(propEl.getAttribute("name"), propEl.getAttribute("value"));
            }
            dsf.setProperties(dsProps);
            envMap.put(id, new Environment(id, tf, dsf.getDataSource()));
        }
        if (envMap.isEmpty()) {
            throw new PersistenceException("配置文件里没有可用的 environment");
        }
        // 选中 default 指定的，否则用第一个
        Environment chosen = defaultId != null ? envMap.get(defaultId) : null;
        if (chosen == null) {
            chosen = envMap.values().iterator().next();
        }
        configuration.setEnvironment(chosen);
    }

    private void parseMappers(Element mappersEl) {
        for (Element mapperEl : childElements(mappersEl)) {
            String resource = mapperEl.getAttribute("resource");
            String className = mapperEl.getAttribute("class");
            if (resource != null && !resource.isEmpty()) {
                try (InputStream is = Resources.getResourceAsStream(resource)) {
                    new XMLMapperBuilder(configuration, is, resource).parse();
                } catch (Exception e) {
                    throw new PersistenceException("解析 Mapper XML 失败：" + resource, e);
                }
            } else if (className != null && !className.isEmpty()) {
                new MapperAnnotationBuilder(configuration, Resources.classForName(className)).parse();
            } else {
                throw new PersistenceException("mapper 必须配置 resource 或 class");
            }
        }
    }

    // ==================================================================
    // DOM 小工具
    // ==================================================================

    private static List<Element> childElements(Element parent) {
        List<Element> list = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static Element findChild(Element parent, String tagName) {
        for (Element child : childElements(parent)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private static Object instantiate(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new PersistenceException("无法实例化 " + clazz.getName()
                    + "（请确认它有 public 无参构造器）", e);
        }
    }
}
