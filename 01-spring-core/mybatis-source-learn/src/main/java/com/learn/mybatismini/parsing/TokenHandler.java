package com.learn.mybatismini.parsing;

/**
 * 令牌处理器（对应 MyBatis 的 {@code TokenHandler}）。
 *
 * <p>与 {@link GenericTokenParser} 配合使用：
 * 解析器负责"找到 {@code #{xxx}} 这种令牌"，处理器负责"决定把它替换成什么"。
 *
 * <pre>
 *   同一个 GenericTokenParser + 不同 TokenHandler = 完全不同的行为
 *
 *   #{} + ParameterMappingTokenHandler  ->  替换成 ?，并记录参数映射
 *   ${} + PropertyTokenHandler          ->  替换成变量的真实值（字符串拼接）
 * </pre>
 * 这是<b>策略模式</b>与<b>关注点分离</b>的漂亮示范：
 * 令牌"识别"的算法只写一遍，令牌"处理"的逻辑可以任意扩展。
 *
 * @author mybatis-source-learn
 */
public interface TokenHandler {

    /**
     * 处理一个令牌的内容。
     *
     * @param content 令牌内部的文本，如 {@code #{userName}} 里的 {@code userName}
     * @return 用来替换整个令牌（含定界符）的字符串
     */
    String handleToken(String content);
}
