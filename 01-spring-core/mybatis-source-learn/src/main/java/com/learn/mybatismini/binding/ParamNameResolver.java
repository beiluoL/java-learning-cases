package com.learn.mybatismini.binding;

import com.learn.mybatismini.annotations.Param;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数名解析器（对应 MyBatis 的 {@code ParamNameResolver}）。
 *
 * <h3>它决定 SQL 里 {@code #{xxx}} 到底去哪取值</h3>
 * <pre>
 *   情况1  单参数、无 @Param：   User selectById(Long id)
 *          → 直接把 Long 当参数对象（#{id} 由 ParameterHandler 特判为整对象值）
 *
 *   情况2  多参数 / 有 @Param：  int count(@Param("min") int, @Param("max") int)
 *          → 包装成 Map：{min=18, max=30, param1=18, param2=30, arg0=18, arg1=30}
 *          → #{min} / #{param1} 都能取到
 * </pre>
 *
 * <p>本项目 pom 开启了 {@code -parameters}，所以<b>不写 @Param 也能用真实参数名</b>
 * （拿到 {@code id} 而非 {@code arg0}）。但这依赖编译参数，生产代码仍建议保留 @Param。
 */
public class ParamNameResolver {

    private final String[] names;

    private final boolean hasParamAnnotation;

    private final int paramCount;

    public ParamNameResolver(Method method) {
        Parameter[] params = method.getParameters();
        this.paramCount = params.length;
        this.names = new String[params.length];
        boolean hasAnno = false;
        for (int i = 0; i < params.length; i++) {
            Param paramAnno = params[i].getAnnotation(Param.class);
            if (paramAnno != null) {
                names[i] = paramAnno.value();
                hasAnno = true;
            } else {
                // 有 -parameters 时就是真实名（id），否则是 arg0
                names[i] = params[i].getName();
            }
        }
        this.hasParamAnnotation = hasAnno;
    }

    /**
     * 把方法实参数组，转成 SQL 能用的参数对象。
     */
    public Object resolveNamedParams(Object[] args) {
        if (args == null) {
            return null;
        }
        if (paramCount == 1 && !hasParamAnnotation) {
            // 单参数且无 @Param：原样返回（Long / User / List...）
            return args[0];
        }
        Map<String, Object> param = new HashMap<>();
        for (int i = 0; i < paramCount; i++) {
            param.put(names[i], args[i]);
            param.put("param" + (i + 1), args[i]);
            param.put("arg" + i, args[i]);
        }
        return param;
    }
}
