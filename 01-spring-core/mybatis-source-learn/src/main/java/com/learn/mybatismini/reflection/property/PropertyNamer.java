package com.learn.mybatismini.reflection.property;

import com.learn.mybatismini.reflection.ReflectionException;

import java.util.Locale;

/**
 * 方法名 &lt;-&gt; 属性名 转换工具（对应 MyBatis 的 {@code PropertyNamer}）。
 *
 * <h3>JavaBean 规范回顾</h3>
 * <pre>
 *   getUserName()  ->  属性 userName
 *   setUserName(x) ->  属性 userName
 *   isActive()     ->  属性 active     （仅 boolean 允许 is 前缀）
 * </pre>
 * 首字母小写的规则有个 <b>坑</b>：如果第 2 个字符也是大写（如 {@code getURL}），
 * 按 JavaBean 规范属性名应保持 {@code URL} 而不是 {@code uRL}。
 * 本类实现了这个规则，MyBatis 源码里也是这么处理的。
 *
 * @author mybatis-source-learn
 */
public final class PropertyNamer {

    private PropertyNamer() {
    }

    /**
     * 由 getter/setter 方法名推导属性名。
     */
    public static String methodToProperty(String name) {
        String prop;
        if (name.startsWith("is")) {
            prop = name.substring(2);
        } else if (name.startsWith("get") || name.startsWith("set")) {
            prop = name.substring(3);
        } else {
            throw new ReflectionException("方法名 '" + name + "' 不符合 JavaBean 规范（必须以 get/set/is 开头）");
        }

        // 首字母小写；但若第 2 个字母也是大写则保持原样（URL、ID 这类缩写）
        if (prop.length() == 1 || (prop.length() > 1 && !Character.isUpperCase(prop.charAt(1)))) {
            prop = prop.substring(0, 1).toLowerCase(Locale.ROOT) + prop.substring(1);
        }
        return prop;
    }

    /** 是否是 getter：getXxx() 或 isXxx() */
    public static boolean isGetter(String name) {
        return (name.startsWith("get") && name.length() > 3)
                || (name.startsWith("is") && name.length() > 2);
    }

    /** 是否是 setter：setXxx(v) */
    public static boolean isSetter(String name) {
        return name.startsWith("set") && name.length() > 3;
    }

    /** 是否是属性访问器（getter 或 setter） */
    public static boolean isProperty(String name) {
        return isGetter(name) || isSetter(name);
    }
}
