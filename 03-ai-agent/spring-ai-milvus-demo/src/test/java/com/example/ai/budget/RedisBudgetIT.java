package com.example.ai.budget;

import com.example.ai.config.AiProperties;
import com.example.ai.metrics.AiMetrics;
import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.mapper.BudgetPolicyMapper;
import com.example.ai.budget.mapper.BudgetReservationMapper;
import com.example.ai.budget.mapper.BudgetTransactionMapper;
import com.example.ai.alert.CostAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 真实 Redis 集成测试（不是 Mockito 模拟 Redis）。连接本机 Redis 的 DB 15，与运行中的应用 DB 0 隔离。
 * 运行：mvn -Pintegration verify
 */
class RedisBudgetIT {

    private static final String USED_KEY = "ai:{budget}:used:GLOBAL:__ALL__:ALL";
    private static final String HELD_KEY = "ai:{budget}:reserved:GLOBAL:__ALL__:ALL";

    private StringRedisTemplate redis;
    private TokenBudgetService service;
    private AiProperties properties;
    private BudgetPolicyMapper policyMapper;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        config.setDatabase(15);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.execute((RedisCallback<Object>) connection -> {
            connection.flushDb();
            return null;
        });

        properties = new AiProperties();
        properties.getBudget().setEnabled(true);
        properties.getBudget().setDefaultMonthlyLimit(new BigDecimal("5.00"));
        properties.getBudget().setReservationTtlSeconds(60);

        policyMapper = mock(BudgetPolicyMapper.class);
        when(policyMapper.findScope(anyString(), anyString())).thenReturn(null);
        when(policyMapper.list()).thenReturn(List.of());
        BudgetReservationMapper reservationMapper = mock(BudgetReservationMapper.class);
        service = new TokenBudgetService(redis,
                script("lua/budget_reserve.lua"), script("lua/budget_settle.lua"),
                script("lua/budget_release.lua"), script("lua/budget_cleanup.lua"),
                policyMapper, reservationMapper, mock(BudgetTransactionMapper.class),
                mock(CostAlertService.class), mock(AiMetrics.class),
                properties, event -> { }, Clock.systemUTC());
    }

    /** 场景预算与全局预算同一次 Lua 内一起校验：任一不足即整单拒绝。 */
    @Test
    void sceneBudgetIsCheckedAtomicallyWithGlobal() {
        BudgetPolicy scene = new BudgetPolicy();
        scene.setBudgetType("SCENE");
        scene.setTargetId("CHAT");
        scene.setDailyLimit(new BigDecimal("2.00"));
        scene.setEnabled(1);
        when(policyMapper.findScope("SCENE", "CHAT")).thenReturn(scene);

        // 全局额度 5.00 充足，但场景额度只有 2.00
        assertTrue(service.reserve("s1", "demo-user", "mock-chat-model", "CHAT", null,
                new BigDecimal("1.50")).approved());
        assertFalse(service.reserve("s2", "demo-user", "mock-chat-model", "CHAT", null,
                new BigDecimal("1.50")).approved(), "场景预算不足必须拒绝，且不得部分扣减");
    }

    @Test
    void normalReserveAndSettle() {
        assertTrue(service.reserve("req-1", "demo-user", "mock-chat-model", new BigDecimal("1.00")).approved());
        assertEquals(100_000_000L, held());
        assertTrue(service.settle("req-1", new BigDecimal("0.30"), false).changed());
        assertEquals(30_000_000L, used());
        assertEquals(0L, held());
    }

    @Test
    void insufficientBudgetRejectsRequest() {
        properties.getBudget().setDefaultMonthlyLimit(new BigDecimal("2.00"));
        assertTrue(service.reserve("a", "demo-user", "mock-chat-model", new BigDecimal("1.50")).approved());
        assertFalse(service.reserve("b", "demo-user", "mock-chat-model", new BigDecimal("1.50")).approved());
        assertEquals(150_000_000L, held());
    }

    @Test
    void hundredConcurrentRequestsNeverExceedBudget() throws Exception {
        properties.getBudget().setDefaultMonthlyLimit(new BigDecimal("5.00"));
        int total = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);
        AtomicInteger approved = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < total; i++) {
            String requestId = "concurrent-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (service.reserve(requestId, "demo-user", "mock-chat-model",
                            new BigDecimal("1.00")).approved()) {
                        approved.incrementAndGet();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "100 并发未在 30 秒内完成");
        pool.shutdownNow();
        assertNull(failure.get(), "并发过程中出现异常: " + failure.get());
        assertEquals(5, approved.get(), "5.00 USD 额度下最多只允许 5 个 1.00 USD 请求通过");
        assertEquals(500_000_000L, held(), "预扣总额不得超过预算上限");
        assertEquals(0L, used());
    }

    @Test
    void duplicateRequestIdAndDuplicateSettleAreIdempotent() {
        assertTrue(service.reserve("dup", "demo-user", "mock-chat-model", new BigDecimal("1.00")).approved());
        assertFalse(service.reserve("dup", "demo-user", "mock-chat-model", new BigDecimal("1.00")).approved());
        assertEquals(100_000_000L, held(), "重复预扣不得重复扣额度");

        assertTrue(service.settle("dup", new BigDecimal("0.40"), false).changed());
        assertFalse(service.settle("dup", new BigDecimal("0.40"), false).changed());
        assertEquals(40_000_000L, used(), "重复结算不得重复扣费");
        assertEquals(0L, held());
    }

    @Test
    void releaseRestoresBudgetOnFailure() {
        service.reserve("rel", "demo-user", "mock-chat-model", new BigDecimal("1.00"));
        service.release("rel");
        assertEquals(0L, held());
        assertEquals(0L, used());
    }

    @Test
    void expiredReservationIsCleanedUp() throws Exception {
        properties.getBudget().setReservationTtlSeconds(1);
        service.reserve("exp", "demo-user", "mock-chat-model", new BigDecimal("1.00"));
        Thread.sleep(1600);
        assertTrue(service.cleanupExpired() >= 1);
        assertEquals(0L, held(), "过期预扣必须同步扣回 reserved 总额");
        assertEquals(0L, used());
    }

    private long used() {
        return read(USED_KEY);
    }

    private long held() {
        return read(HELD_KEY);
    }

    private long read(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0L : Long.parseLong(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DefaultRedisScript<List> script(String location) {
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource(location));
        s.setResultType((Class) List.class);
        return s;
    }
}
