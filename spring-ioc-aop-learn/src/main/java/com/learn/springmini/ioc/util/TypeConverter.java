package com.learn.springmini.ioc.util;

import com.learn.springmini.ioc.BeansException;

/**
 * 类型转换器 —— 对应 Spring 的 {@code TypeConverter} / {@code SimpleTypeConverter}。
 *
 * <p>XML 里读出来的属性值一律是字符串（如 {@code value="18"}），但 Java 字段可能是
 * {@code int}、{@code boolean}、{@code Integer}……这个工具负责把字符串“智能地”转成目标类型。
 * 它体现了 IoC 容器“按目标字段类型做适配注入”的精髓。</p>
 */
public final class TypeConverter {

    private TypeConverter() {
    }

    /**
     * 把 {@code rawValue} 转换成 {@code targetType} 类型。
     *
     * @param rawValue    原始值（可能是已就绪的 Bean 实例，也可能是待转换的字符串）
     * @param targetType  目标类型（setter 形参类型 / 构造参数类型）
     * @return 转换后的对象
     */
    public static Object convertIfNecessary(Object rawValue, Class<?> targetType) {
        // 1) 已经是目标类型（或可赋值）的实例，例如引用注入得到的 Bean 对象，直接返回。
        if (rawValue == null) {
            return null;
        }
        if (targetType.isInstance(rawValue)) {
            return rawValue;
        }
        // 2) 非 String 且类型不匹配：交给调用方去按引用/复杂类型处理（这里只处理字符串字面量）。
        if (!(rawValue instanceof String)) {
            throw new BeansException("无法将 " + rawValue.getClass().getName()
                    + " 转换为 " + targetType.getName());
        }
        String text = ((String) rawValue).trim();
        return doConvertStringToType(text, targetType);
    }

    private static Object doConvertStringToType(String text, Class<?> targetType) {
        // 字符串 → 字符串（最常见）
        if (targetType == String.class) {
            return text;
        }
        // 基本类型 / 包装类型
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(text);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(text);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(text);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(text);
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(text);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(text);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(text);
        }
        if (targetType == char.class || targetType == Character.class) {
            if (text.isEmpty()) {
                throw new BeansException("无法将空字符串转换为 char");
            }
            return text.charAt(0);
        }
        throw new BeansException("暂不支持将字符串转换为类型: " + targetType.getName());
    }
}
