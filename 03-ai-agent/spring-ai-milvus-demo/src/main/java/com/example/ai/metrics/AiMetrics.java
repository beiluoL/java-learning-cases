package com.example.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/** 平台可观测性指标，暴露到 /actuator/metrics 与 /actuator/prometheus（若接入）。 */
@Component
public class AiMetrics {

    private final Counter requests;
    private final Counter rejected;
    private final Counter redisFailures;
    private final Counter alerts;
    private final Timer latency;
    private final DistributionSummary cost;
    private final Counter cacheHits;
    private final Counter protectionBlocked;
    private final Counter rateLimited;
    private final Counter routeDowngrades;

    public AiMetrics(MeterRegistry registry) {
        this.requests = Counter.builder("ai.request.total").description("AI 请求总量").register(registry);
        this.rejected = Counter.builder("ai.budget.rejected").description("预算拒绝次数").register(registry);
        this.redisFailures = Counter.builder("ai.budget.redis.failure").description("Redis 预算操作失败次数").register(registry);
        this.alerts = Counter.builder("ai.cost.alert").description("成本异常事件数").register(registry);
        this.latency = Timer.builder("ai.request.latency").description("模型调用延迟").register(registry);
        this.cost = DistributionSummary.builder("ai.cost.amount").description("单次成本(USD)").register(registry);
        this.cacheHits = Counter.builder("ai.cache.hit").description("响应缓存命中次数").register(registry);
        this.protectionBlocked = Counter.builder("ai.protection.blocked").description("被保护策略拦截次数").register(registry);
        this.rateLimited = Counter.builder("ai.rate.limited").description("被限流次数").register(registry);
        this.routeDowngrades = Counter.builder("ai.route.downgrade").description("模型降级次数").register(registry);
    }

    public void request() {
        requests.increment();
    }

    public void rejected() {
        rejected.increment();
    }

    public void redisFailure() {
        redisFailures.increment();
    }

    public void alert() {
        alerts.increment();
    }

    public void latency(long millis) {
        latency.record(millis, TimeUnit.MILLISECONDS);
    }

    public void cost(BigDecimal amount) {
        cost.record(amount.doubleValue());
    }

    public void cacheHit() {
        cacheHits.increment();
    }

    public void protectionBlocked() {
        protectionBlocked.increment();
    }

    public void rateLimited() {
        rateLimited.increment();
    }

    public void routeDowngrade() {
        routeDowngrades.increment();
    }
}
