package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;
import com.learn.springmini.ioc.beans.BeanReference;
import com.learn.springmini.ioc.beans.ConstructorArgumentValue;
import com.learn.springmini.ioc.beans.ConstructorArgumentValues;
import com.learn.springmini.ioc.beans.PropertyValue;
import com.learn.springmini.ioc.beans.PropertyValues;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/**
 * XML 形式的 Bean 定义读取器 —— 对应 Spring 的 {@code XmlBeanDefinitionReader}。
 *
 * <p>它演示了 IoC 容器“配置驱动”的第一环：把 XML 配置解析成内存中的 BeanDefinition。</p>
 *
 * <h3>支持的 XML 写法</h3>
 * <pre>{@code
 * <beans>
 *   <bean id="userDao" class="com.xxx.UserDaoImpl"/>
 *   <bean id="userService" class="com.xxx.UserServiceImpl" scope="singleton">
 *     <!-- 构造器注入 -->
 *     <constructor-arg index="0" ref="userDao"/>
 *     <!-- Setter 注入 -->
 *     <property name="appName" value="Mini-Spring"/>
 *   </bean>
 * </beans>
 * }</pre>
 */
public class XmlBeanDefinitionReader implements BeanDefinitionReader {

    /** 注册表（实际就是我们的 DefaultListableBeanFactory）。 */
    private final BeanDefinitionRegistry registry;

    /** 为名不为空的匿名 <bean> 自动编号。 */
    private int anonymousBeanCount = 0;

    public XmlBeanDefinitionReader(BeanDefinitionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void loadBeanDefinitions(String location) throws BeansException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        if (is == null) {
            // 兼容以 "/" 开头的路径
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(location.replaceFirst("^/", ""));
        }
        if (is == null) {
            throw new BeansException("找不到 XML 配置文件: " + location);
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            doc.getDocumentElement().normalize();
            doLoadBeanDefinitions(doc);
        } catch (BeansException e) {
            throw e;
        } catch (Exception e) {
            throw new BeansException("解析 XML 配置文件 [" + location + "] 失败", e);
        }
    }

    private void doLoadBeanDefinitions(Document doc) {
        NodeList beanNodes = doc.getElementsByTagName("bean");
        for (int i = 0; i < beanNodes.getLength(); i++) {
            Node node = beanNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            parseBeanElement((Element) node);
        }
    }

    private void parseBeanElement(Element beanElement) {
        String id = beanElement.getAttribute("id");
        String className = beanElement.getAttribute("class");
        if (className == null || className.isEmpty()) {
            throw new BeansException("每个 <bean> 都必须有 class 属性");
        }
        // 没有显式 id 时自动生成（与 Spring 类似：类名#序号），保证 key 唯一
        if (id == null || id.isEmpty()) {
            id = className + "#" + (anonymousBeanCount++);
        }

        BeanDefinition bd = new BeanDefinition(className);

        String scope = beanElement.getAttribute("scope");
        if (scope != null && !scope.isEmpty()) {
            bd.setScope(scope);
        }
        String lazy = beanElement.getAttribute("lazy-init");
        if ("true".equalsIgnoreCase(lazy)) {
            bd.setLazyInit(true);
        }
        String initMethod = beanElement.getAttribute("init-method");
        if (initMethod != null && !initMethod.isEmpty()) {
            bd.setInitMethodName(initMethod);
        }

        // 解析子元素：constructor-arg 与 property
        ConstructorArgumentValues cav = new ConstructorArgumentValues();
        PropertyValues pvs = new PropertyValues();
        int constructorArgCounter = 0;

        NodeList children = beanElement.getChildNodes();
        for (int j = 0; j < children.getLength(); j++) {
            Node child = children.item(j);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childEl = (Element) child;
            String childName = childEl.getLocalName();
            if ("constructor-arg".equals(childName)) {
                cav.addArgumentValue(parseConstructorArg(childEl, constructorArgCounter++));
            } else if ("property".equals(childName)) {
                pvs.addPropertyValue(parseProperty(childEl));
            }
        }
        bd.setConstructorArgumentValues(cav);
        bd.setPropertyValues(pvs);

        registry.registerBeanDefinition(id, bd);
    }

    /** 解析一个 {@code <constructor-arg>}：仅支持 ref 或 value，可用 index 指定位置。 */
    private ConstructorArgumentValue parseConstructorArg(Element el, int defaultIndex) {
        String indexAttr = el.getAttribute("index");
        int index = indexAttr != null && !indexAttr.isEmpty()
                ? Integer.parseInt(indexAttr) : defaultIndex;

        String ref = el.getAttribute("ref");
        String value = el.getAttribute("value");
        if (ref != null && !ref.isEmpty()) {
            return new ConstructorArgumentValue(index, new BeanReference(ref));
        }
        if (value != null && !value.isEmpty()) {
            return new ConstructorArgumentValue(index, value);
        }
        throw new BeansException("<constructor-arg> 必须指定 ref 或 value");
    }

    /** 解析一个 {@code <property>}：仅支持 ref 或 value。 */
    private PropertyValue parseProperty(Element el) {
        String name = el.getAttribute("name");
        if (name == null || name.isEmpty()) {
            throw new BeansException("<property> 必须指定 name");
        }
        String ref = el.getAttribute("ref");
        String value = el.getAttribute("value");
        if (ref != null && !ref.isEmpty()) {
            return new PropertyValue(name, new BeanReference(ref));
        }
        if (value != null && !value.isEmpty()) {
            return new PropertyValue(name, value);
        }
        throw new BeansException("<property> 必须指定 ref 或 value");
    }
}
