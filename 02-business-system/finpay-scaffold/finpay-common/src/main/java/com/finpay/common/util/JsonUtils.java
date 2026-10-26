package com.finpay.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

/**
 * JSON 工具封装。统一使用 Fastjson2，关闭循环引用、保持字段顺序。
 */
public final class JsonUtils {

    private JsonUtils() {}

    public static String toJson(Object obj) {
        return JSON.toJSONString(obj, JSONWriter.Feature.WriteNulls);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    public static <T> T fromJson(String json, java.lang.reflect.Type type) {
        return JSON.parseObject(json, type);
    }
}
