package com.learn.mybatismini.executor.keygen;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.mapping.MappedStatement;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * "什么也不做"的主键生成器（空对象模式）。
 *
 * <p>绝大多数查询、以及没有 {@code useGeneratedKeys} 的写操作都挂它，
 * 这样调用方无需判空，直接 {@code keyGenerator.processAfter(...)} 即可。
 */
public class NoKeyGenerator implements KeyGenerator {

    @Override
    public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        // 空实现
    }

    @Override
    public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        // 空实现
    }
}
