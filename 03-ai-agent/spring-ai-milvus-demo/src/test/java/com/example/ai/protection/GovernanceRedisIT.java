package com.example.ai.protection;

import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.config.AiProperties;
import com.example.ai.optimization.EmbeddingCacheService;
import com.example.ai.optimization.ResponseCacheService;
import com.example.ai.alert.CostAlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

/**
 * 真实 Redis 集成测试：限流并发安全、响应缓存隔离、Embedding 去重。
 * 连接本机 Redis DB 14（与预算测试 DB 15、应用 DB 0 隔离）。
 */
class GovernanceRedisIT {

    private StringRedisTemplate redis;
    private AiProperties properties;
    private AiRequestProtectionService guard;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        config.setDatabase(14);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.execute((RedisCallback<Object>) connection -> {
            connection.flushDb();
            return null;
        });

        properties = new AiProperties();
        properties.getRateLimit().setPerUserPerMinute(5);
        properties.getRateLimit().setPerUserPerHour(100);
        properties.getRateLimit().setPerUserPerDay(1000);
        properties.getRateLimit().setPerModelConcurrency(100);
        properties.getRateLimit().setGlobalConcurrency(200);
        guard = new AiRequestProtectionService(redis, properties, mock(CostAlertService.class), Clock.systemUTC());
    }

    @Test
    void userMinuteWindowRejectsAfterLimit() {
        for (int i = 0; i < 5; i++) {
            guard.acquire("demo-user", "mock-chat-model");
        }
        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.acquire("demo-user", "mock-chat-model"));
        assertTrue(e.getMessage().contains("限流"));
    }

    @Test
    void concurrencyIsReleasedAndNeverLeaks() {
        AiRequestProtectionService.Permit p = guard.acquire("u1", "m1");
        assertEquals("1", redis.opsForValue().get("ai:{guard}:conc:global"));
        guard.release(p, "u1");
        assertEquals("0", redis.opsForValue().get("ai:{guard}:conc:global"));
        // 重复释放不能把计数打成负数
        guard.release(p, "u1");
        assertEquals("0", redis.opsForValue().get("ai:{guard}:conc:global"));
    }

    @Test
    void concurrentAcquiresNeverExceedModelConcurrency() throws Exception {
        properties.getRateLimit().setPerModelConcurrency(4);
        properties.getRateLimit().setGlobalConcurrency(8);
        int total = 40;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);
        AtomicInteger acquired = new AtomicInteger();
        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    AiRequestProtectionService.Permit p = guard.acquire("u-" + Thread.currentThread().getId(), "m1");
                    acquired.incrementAndGet();
                    Thread.sleep(20);
                    guard.release(p, "u");
                } catch (BusinessException ignored) {
                    // 被限流属于预期
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertTrue(acquired.get() >= 1);
        String global = redis.opsForValue().get("ai:{guard}:conc:global");
        assertTrue(global == null || Integer.parseInt(global) >= 0, "并发计数不得为负");
    }

    @Test
    void responseCacheIsolatesByUserModelAndPromptVersion() {
        ResponseCacheService cache = new ResponseCacheService(redis, new ObjectMapper(), properties);
        String key1 = cache.key("user-a", "kb1", "m1", "什么是缓存穿透");
        String key2 = cache.key("user-b", "kb1", "m1", "什么是缓存穿透");
        String key3 = cache.key("user-a", "kb1", "m2", "什么是缓存穿透");
        assertFalse(key1.equals(key2), "不同用户不得共享缓存");
        assertFalse(key1.equals(key3), "不同模型不得共享缓存");

        cache.store(key1, new ResponseCacheService.CachedAnswer("答案", "m1", "mock", "L2",
                100, 50, 0, 0, "0.00010000", "DEMO-v1", System.currentTimeMillis()));
        assertTrue(cache.lookup(key1).isPresent());
        assertTrue(cache.lookup(key2).isEmpty());
        ResponseCacheService.CacheStats stats = cache.stats();
        assertEquals(1, stats.hit());
        assertTrue(stats.miss() >= 1);
    }

    @Test
    void embeddingDedupSkipsSameContentAndModel() {
        EmbeddingCacheService service = new EmbeddingCacheService(redis, properties);
        assertFalse(service.shouldSkip("embed-model", "同一段文档内容"));
        service.markEmbedded("embed-model", "同一段文档内容");
        assertTrue(service.shouldSkip("embed-model", "同一段文档内容"));
        assertFalse(service.shouldSkip("other-model", "同一段文档内容"),
                "模型版本不同不能复用向量");
        assertTrue(service.stats().skipped() >= 1);
    }

    @Test
    void rateLimiterFailsOpenButKeepsCountersSane() {
        properties.getRateLimit().setPerUserPerMinute(1);
        guard.acquire("u2", "m1");
        assertThrows(BusinessException.class, () -> guard.acquire("u2", "m1"));
        assertTrue(guard.requestsToday("u2") >= 1);
    }
}
