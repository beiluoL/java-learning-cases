package com.example.ai.token.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link UsageQuery} 排序白名单回归测试。
 *
 * <p>存在意义：{@code sort} 会以 {@code ORDER BY ${sort}} 形式拼入 SQL，
 * 属于字符串插值而非参数绑定。这里锁定「任何非法输入都必须回落为常量」，
 * 避免后续把校验逻辑搬回 Service 而重新打开注入面。</p>
 */
class UsageQueryTest {

    @Test
    void keepsWhitelistedSortAsIs() {
        UsageQuery q = new UsageQuery();
        q.setSort("total_cost DESC");
        assertEquals("total_cost DESC", q.getSort());
    }

    @Test
    void mapsCamelCaseAliasToColumnExpression() {
        UsageQuery q = new UsageQuery();
        q.setSort("latencyMs,DESC");
        assertEquals("latency_ms DESC", q.getSort());
    }

    @Test
    void fallsBackToDefaultOnSqlInjectionAttempt() {
        UsageQuery q = new UsageQuery();
        q.setSort("created_at DESC; DROP TABLE ai_token_usage");
        assertEquals(UsageQuery.DEFAULT_SORT, q.getSort());
    }

    @Test
    void fallsBackToDefaultOnUnknownColumn() {
        UsageQuery q = new UsageQuery();
        q.setSort("secret_column ASC");
        assertEquals(UsageQuery.DEFAULT_SORT, q.getSort());
    }

    @Test
    void fallsBackToDefaultOnNull() {
        UsageQuery q = new UsageQuery();
        q.setSort(null);
        assertEquals(UsageQuery.DEFAULT_SORT, q.getSort());
    }

    @Test
    void fallsBackToDefaultOnBlank() {
        UsageQuery q = new UsageQuery();
        q.setSort("   ");
        assertEquals(UsageQuery.DEFAULT_SORT, q.getSort());
    }

    @Test
    void defaultSortIsWhitelisted() {
        assertEquals(true, UsageQuery.ALLOWED_SORT.contains(UsageQuery.DEFAULT_SORT));
    }
}
