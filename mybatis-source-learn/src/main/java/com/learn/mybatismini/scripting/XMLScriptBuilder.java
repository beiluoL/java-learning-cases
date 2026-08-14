package com.learn.mybatismini.scripting;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.session.Configuration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XML 动态 SQL 解析器（对应 MyBatis 的 {@code XMLScriptBuilder}）。
 *
 * <h3>把 &lt;select&gt; 里的标签树，翻译成一棵 SqlNode 树</h3>
 * <pre>
 *   &lt;select&gt;
 *     SELECT ... FROM t_user
 *     &lt;where&gt;
 *       &lt;if test="userName != null"&gt;AND user_name = #{userName}&lt;/if&gt;
 *     &lt;/where&gt;
 *   &lt;/select&gt;
 *        │  逐节点翻译
 *        ▼
 *   MixedSqlNode
 *     ├─ TextSqlNode("SELECT ... FROM t_user")
 *     └─ WhereSqlNode
 *          └─ IfSqlNode(test="userName != null", TextSqlNode("AND user_name = #{userName}"))
 * </pre>
 *
 * <p>解析完成后，{@link #isDynamic()} 告诉我们这棵 SQL 是否含动态标签 / ${}。
 * 若为 false，外层会把它包成 {@code RawSqlSource}（只解析一次，性能最好）；
 * 若为 true，则包成 {@code DynamicSqlSource}（每次调用重新拼）。
 */
public class XMLScriptBuilder {

    private final Configuration configuration;

    private final Node statementNode;

    private final Class<?> parameterType;

    /** 当前 Mapper 的 &lt;sql&gt; 片段：shortId -> 文本 */
    private final Map<String, String> sqlFragments;

    private final String namespace;

    /** 是否包含动态标签或 ${}（决定 Raw / Dynamic） */
    private boolean isDynamic;

    public XMLScriptBuilder(Configuration configuration, Node statementNode, Class<?> parameterType,
                            Map<String, String> sqlFragments, String namespace) {
        this.configuration = configuration;
        this.statementNode = statementNode;
        this.parameterType = parameterType;
        this.sqlFragments = sqlFragments;
        this.namespace = namespace;
    }

    /** 解析成根 SqlNode */
    public SqlNode parseScriptNode() {
        List<SqlNode> contents = parseDynamicTags(statementNode);
        return new MixedSqlNode(contents);
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    // ==================================================================
    // 递归解析子节点
    // ==================================================================

    private List<SqlNode> parseDynamicTags(Node node) {
        List<SqlNode> nodes = new ArrayList<>();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            int type = child.getNodeType();
            if (type == Node.CDATA_SECTION_NODE || type == Node.TEXT_NODE) {
                String data = child.getNodeValue();
                if (data == null || data.isBlank()) {
                    continue;
                }
                // 文本节点（含 #{...} 会被 SqlSourceBuilder 统一处理；含 ${} 则视为动态）
                TextSqlNode textNode = new TextSqlNode(data);
                if (textNode.isDynamic()) {
                    isDynamic = true;
                }
                nodes.add(textNode);
            } else if (type == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                SqlNode handled = dispatchElement(name, child);
                if (handled != null) {
                    nodes.add(handled);
                }
            }
        }
        return nodes;
    }

    private SqlNode dispatchElement(String name, Node node) {
        switch (name) {
            case "if":
                isDynamic = true;
                return new IfSqlNode(attr(node, "test"), new MixedSqlNode(parseDynamicTags(node)));
            case "where":
                isDynamic = true;
                return new WhereSqlNode(configuration, new MixedSqlNode(parseDynamicTags(node)));
            case "set":
                isDynamic = true;
                return new SetSqlNode(configuration, new MixedSqlNode(parseDynamicTags(node)));
            case "trim":
                isDynamic = true;
                return new TrimSqlNode(configuration, new MixedSqlNode(parseDynamicTags(node)),
                        attr(node, "prefix"),
                        split(attr(node, "prefixesToOverride")),
                        split(attr(node, "suffixesToOverride")));
            case "foreach":
                isDynamic = true;
                return new ForEachSqlNode(configuration, new MixedSqlNode(parseDynamicTags(node)),
                        attr(node, "collection"), attr(node, "item"), attr(node, "index"),
                        attr(node, "open"), attr(node, "close"), attr(node, "separator"));
            case "choose":
                isDynamic = true;
                return parseChoose(node);
            case "include":
                return parseInclude(node);
            case "bind":
                // 本简化版暂不实现 <bind>，跳过（不影响已配置的示例）
                return null;
            default:
                // 未知标签：作为纯文本忽略，避免报错
                return null;
        }
    }

    private SqlNode parseChoose(Node node) {
        List<ChooseSqlNode.When> whens = new ArrayList<>();
        SqlNode otherwise = null;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String tag = child.getNodeName();
            if ("when".equals(tag)) {
                whens.add(new ChooseSqlNode.When(attr(child, "test"),
                        new MixedSqlNode(parseDynamicTags(child))));
            } else if ("otherwise".equals(tag)) {
                otherwise = new MixedSqlNode(parseDynamicTags(child));
            }
        }
        return new ChooseSqlNode(whens, otherwise);
    }

    private SqlNode parseInclude(Node node) {
        String refid = attr(node, "refid");
        String key = refid;
        String text = sqlFragments.get(key);
        if (text == null) {
            text = sqlFragments.get(namespace + "." + key);
        }
        if (text == null) {
            throw new PersistenceException("找不到 sql 片段：" + refid
                    + "（请检查 <sql id=\"" + refid + "\"> 是否已定义）");
        }
        return new TextSqlNode(text);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (String s : value.split("\\|")) {
            list.add(s.trim());
        }
        return list;
    }

    private static String attr(Node node, String name) {
        if (node instanceof Element element) {
            return element.getAttribute(name);
        }
        return "";
    }
}
