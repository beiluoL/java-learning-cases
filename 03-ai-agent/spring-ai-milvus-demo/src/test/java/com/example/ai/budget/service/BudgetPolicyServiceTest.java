package com.example.ai.budget.service;

import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.mapper.BudgetPolicyMapper;
import com.example.ai.budget.mapper.BudgetTransactionMapper;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预算策略服务：NOT NULL 列默认值补全与时间基准的回归测试。
 *
 * <p><b>为什么必须存在：</b>2026-11-11 实测发现两个预算创建接口
 * （{@code /api/admin/ai/budgets} 与 {@code /api/admin/budget}）在客户端省略
 * 「有数据库默认值」的字段时<b>必然 500</b>。根因是 MyBatis 的 INSERT 把所有列都写成
 * 显式占位符，Java 传 null 会真的插入 NULL，数据库列默认值不生效。
 *
 * <p>这里的用例逐个锁定 {@code allow_overrun} / {@code enabled} / {@code currency} /
 * {@code overrun_policy} 四个 NOT NULL 列都必须被补全，防止有人把默认值逻辑删掉。</p>
 */
class BudgetPolicyServiceTest {

    /** 固定时钟：让流水查询窗口可断言，同时证明取的是 UTC 而非本机时区。 */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T06:00:00Z"), ZoneOffset.UTC);

    private BudgetPolicyMapper policyMapper;
    private BudgetTransactionMapper txMapper;
    private BudgetPolicyService service;

    @BeforeEach
    void setUp() {
        policyMapper = mock(BudgetPolicyMapper.class);
        txMapper = mock(BudgetTransactionMapper.class);
        service = new BudgetPolicyService(policyMapper, txMapper, CLOCK);
    }

    // ---------- create：NOT NULL 默认值补全 ----------

    @Test
    void createFillsAllNotNullColumnDefaults() {
        BudgetPolicy input = new BudgetPolicy();
        input.setBudgetType("USER");
        input.setTargetId("u1");
        input.setDailyLimit(new BigDecimal("10.00"));

        service.create(input);

        ArgumentCaptor<BudgetPolicy> captor = ArgumentCaptor.forClass(BudgetPolicy.class);
        verify(policyMapper).insert(captor.capture());
        BudgetPolicy inserted = captor.getValue();

        assertEquals(1, inserted.getEnabled(), "enabled 是 NOT NULL 列，必须补 1");
        assertEquals(0, inserted.getAllowOverrun(), "allow_overrun 是 NOT NULL 列，必须补 0");
        assertEquals("USD", inserted.getCurrency(), "currency 是 NOT NULL 列，必须补 USD");
        assertEquals("BLOCK", inserted.getOverrunPolicy(), "overrun_policy 是 NOT NULL 列，必须补 BLOCK");
    }

    @Test
    void createKeepsCallerSuppliedValues() {
        BudgetPolicy input = new BudgetPolicy();
        input.setBudgetType("SCENE");
        input.setTargetId("RAG");
        input.setEnabled(0);
        input.setAllowOverrun(1);
        input.setCurrency("CNY");
        input.setOverrunPolicy("DOWNGRADE");

        service.create(input);

        ArgumentCaptor<BudgetPolicy> captor = ArgumentCaptor.forClass(BudgetPolicy.class);
        verify(policyMapper).insert(captor.capture());
        BudgetPolicy inserted = captor.getValue();

        assertEquals(0, inserted.getEnabled());
        assertEquals(1, inserted.getAllowOverrun());
        assertEquals("CNY", inserted.getCurrency());
        assertEquals("DOWNGRADE", inserted.getOverrunPolicy());
    }

    @Test
    void createTreatsBlankStringAsMissing() {
        BudgetPolicy input = new BudgetPolicy();
        input.setBudgetType("USER");
        input.setTargetId("u1");
        input.setCurrency("   ");
        input.setOverrunPolicy("");

        service.create(input);

        ArgumentCaptor<BudgetPolicy> captor = ArgumentCaptor.forClass(BudgetPolicy.class);
        verify(policyMapper).insert(captor.capture());
        assertEquals("USD", captor.getValue().getCurrency());
        assertEquals("BLOCK", captor.getValue().getOverrunPolicy());
    }

    @Test
    void createClearsInjectedId() {
        BudgetPolicy input = new BudgetPolicy();
        input.setId(999L);
        input.setBudgetType("GLOBAL");
        input.setTargetId("__ALL__");

        service.create(input);

        ArgumentCaptor<BudgetPolicy> captor = ArgumentCaptor.forClass(BudgetPolicy.class);
        verify(policyMapper).insert(captor.capture());
        assertNull(captor.getValue().getId(), "调用方传入的 id 必须被清空，避免误更新既有记录");
    }

    @Test
    void createRejectsMissingRequiredFields() {
        BudgetPolicy noType = new BudgetPolicy();
        noType.setTargetId("u1");
        assertThrows(BusinessException.class, () -> service.create(noType));

        BudgetPolicy noTarget = new BudgetPolicy();
        noTarget.setBudgetType("USER");
        assertThrows(BusinessException.class, () -> service.create(noTarget));

        assertThrows(BusinessException.class, () -> service.create(null));
    }

    @Test
    void createReturnsPersistedRowWithGeneratedId() {
        BudgetPolicy input = new BudgetPolicy();
        input.setBudgetType("USER");
        input.setTargetId("u1");

        BudgetPolicy persisted = new BudgetPolicy();
        persisted.setId(8L);
        persisted.setOverrunPolicy("BLOCK");
        when(policyMapper.findById(any())).thenReturn(persisted);

        BudgetPolicy result = service.create(input);

        assertNotNull(result);
        assertEquals(8L, result.getId(), "返回值必须是落库后的行（含自增主键），而不是入参对象");
    }

    // ---------- update：NOT NULL 列回填 ----------

    @Test
    void updateBackfillsNotNullColumnsFromExistingRow() {
        BudgetPolicy existing = new BudgetPolicy();
        existing.setId(1L);
        existing.setEnabled(1);
        existing.setAllowOverrun(0);
        existing.setOverrunPolicy("DOWNGRADE");
        when(policyMapper.findById(1L)).thenReturn(existing);

        BudgetPolicy patch = new BudgetPolicy();
        patch.setDailyLimit(new BigDecimal("20.00"));

        service.update(1L, patch);

        ArgumentCaptor<BudgetPolicy> captor = ArgumentCaptor.forClass(BudgetPolicy.class);
        verify(policyMapper).update(captor.capture());
        BudgetPolicy updated = captor.getValue();

        assertEquals(1L, updated.getId());
        assertEquals(1, updated.getEnabled(), "请求体缺失 enabled 时必须沿用原值，否则以 null 覆盖 NOT NULL 列");
        assertEquals(0, updated.getAllowOverrun());
        assertEquals("DOWNGRADE", updated.getOverrunPolicy());
    }

    @Test
    void updateKeepsCallerSuppliedValues() {
        BudgetPolicy existing = new BudgetPolicy();
        existing.setId(1L);
        existing.setEnabled(1);
        existing.setAllowOverrun(0);
        existing.setOverrunPolicy("BLOCK");
        when(policyMapper.findById(1L)).thenReturn(existing);

        BudgetPolicy patch = new BudgetPolicy();
        patch.setEnabled(0);
        patch.setAllowOverrun(1);
        patch.setOverrunPolicy("ALLOW");

        service.update(1L, patch);

        ArgumentCaptor<BudgetPolicy> captor = ArgumentCaptor.forClass(BudgetPolicy.class);
        verify(policyMapper).update(captor.capture());
        assertEquals(0, captor.getValue().getEnabled());
        assertEquals(1, captor.getValue().getAllowOverrun());
        assertEquals("ALLOW", captor.getValue().getOverrunPolicy());
    }

    @Test
    void updateThrowsWhenPolicyMissing() {
        when(policyMapper.findById(anyLong())).thenReturn(null);
        BudgetPolicy patch = new BudgetPolicy();
        assertThrows(BusinessException.class, () -> service.update(404L, patch));
    }

    // ---------- page ----------

    @Test
    void pageFiltersByBudgetTypeIgnoringCase() {
        when(policyMapper.list()).thenReturn(List.of(
                policy(1L, "USER"), policy(2L, "user"), policy(3L, "GLOBAL")));

        PageResult<BudgetPolicy> result = service.page(1, 20, "User");

        assertEquals(2, result.total());
        assertEquals(2, result.records().size());
    }

    @Test
    void pageClampsSizeAndPage() {
        when(policyMapper.list()).thenReturn(List.of(policy(1L, "USER")));

        PageResult<BudgetPolicy> result = service.page(-5, 9999, null);

        assertEquals(1, result.page(), "页码下限为 1");
        assertEquals(100, result.size(), "单页上限为 100");
    }

    @Test
    void pageReturnsEmptySliceWhenPageBeyondData() {
        when(policyMapper.list()).thenReturn(List.of(policy(1L, "USER")));

        PageResult<BudgetPolicy> result = service.page(5, 20, null);

        assertEquals(0, result.records().size());
        assertEquals(1, result.total(), "total 反映过滤后的总数，不随页码变化");
    }

    // ---------- transactions：时间基准 ----------

    @Test
    void transactionsUsesInjectedUtcClock() {
        when(txMapper.page(any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(txMapper.count(any(), any(), any(), any())).thenReturn(0L);

        service.transactions(1, 20, null, null, 7L);

        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(txMapper).page(any(), any(), fromCaptor.capture(), toCaptor.capture(), anyLong(), anyInt());

        // 时钟固定在 2026-11-11T06:00Z，to 再多给 1 分钟容忍写入延迟
        assertEquals(LocalDateTime.parse("2026-11-11T06:01:00"), toCaptor.getValue());
        assertEquals(LocalDateTime.parse("2026-11-04T06:01:00"), fromCaptor.getValue());
    }

    @Test
    void transactionsClampsLookbackDays() {
        when(txMapper.page(any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(txMapper.count(any(), any(), any(), any())).thenReturn(0L);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        service.transactions(1, 20, null, null, 999L);
        verify(txMapper).page(any(), any(), fromCaptor.capture(), toCaptor.capture(), anyLong(), anyInt());
        assertEquals(LocalDateTime.parse("2026-08-11T06:01:00"), fromCaptor.getValue(),
                "回看天数上限 92 天");

        service.transactions(1, 20, null, null, 0L);
        verify(txMapper, times(2))
                .page(any(), any(), fromCaptor.capture(), toCaptor.capture(), anyLong(), anyInt());
        assertEquals(LocalDateTime.parse("2026-11-10T06:01:00"), fromCaptor.getValue(),
                "回看天数下限 1 天");
    }

    @Test
    void transactionsDefaultsToSevenDaysWhenNull() {
        when(txMapper.page(any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(txMapper.count(any(), any(), any(), any())).thenReturn(0L);

        service.transactions(1, 20, null, null, null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(txMapper).page(any(), any(), fromCaptor.capture(), any(), anyLong(), anyInt());
        assertEquals(LocalDateTime.parse("2026-11-04T06:01:00"), fromCaptor.getValue());
    }

    @Test
    void transactionsPassesPagingOffset() {
        when(txMapper.page(any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(txMapper.count(any(), any(), any(), any())).thenReturn(0L);

        service.transactions(3, 20, null, null, null);

        verify(txMapper).page(any(), any(), any(), any(), eq(40L), eq(20));
    }

    // ---------- getRequired ----------

    @Test
    void getRequiredThrowsWhenMissing() {
        when(policyMapper.findById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getRequired(404L));
    }

    @Test
    void getRequiredReturnsRow() {
        BudgetPolicy p = policy(1L, "USER");
        when(policyMapper.findById(1L)).thenReturn(p);
        assertEquals(p, service.getRequired(1L));
    }

    private static BudgetPolicy policy(Long id, String budgetType) {
        BudgetPolicy p = new BudgetPolicy();
        p.setId(id);
        p.setBudgetType(budgetType);
        p.setTargetId("t" + id);
        return p;
    }
}
