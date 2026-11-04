package com.example.mq.mq;

import com.example.mq.common.mq.MqScene;
import com.example.mq.dto.ChaosConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 故障注入开关（教学专用）。
 * <p>
 * 打开后消费者会按设定概率抛异常，于是可以在页面上完整观察这条链路：
 * <pre>
 *   业务线程正常消费
 *     └─ 抛异常 → 进重试队列（5 秒 TTL）
 *          └─ 回投业务队列 → 再失败 → 第 2、3 次
 *               └─ 超过 max-retry-count → 死信队列
 *                    └─ 修复后点「死信回放」→ 重新投递成功
 * </pre>
 * 生产环境请务必删除此类组件。
 */
@Slf4j
@Component
public class ChaosSwitch {

    private final Map<MqScene, AtomicInteger> failRates = new ConcurrentHashMap<>();

    public void configure(MqScene scene, boolean enabled, int failRate) {
        int rate = enabled ? Math.max(0, Math.min(100, failRate)) : 0;
        failRates.computeIfAbsent(scene, k -> new AtomicInteger(0)).set(rate);
        log.warn("[CHAOS] 场景 {} 故障注入已{}，失败概率 {}%", scene, enabled ? "开启" : "关闭", rate);
    }

    /** 命中则抛异常，模拟下游不可用 */
    public void maybeFail(MqScene scene) {
        AtomicInteger rate = failRates.get(scene);
        if (rate == null || rate.get() <= 0) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) < rate.get()) {
            throw new IllegalStateException("[CHAOS] 人为注入的消费失败：" + scene);
        }
    }

    public Map<String, ChaosConfig> snapshot() {
        Map<String, ChaosConfig> result = new LinkedHashMap<>();
        for (MqScene scene : MqScene.values()) {
            // 只暴露真正有消费者的场景：给发布方注入故障没有意义
            if (!scene.isConsumable()) {
                continue;
            }
            AtomicInteger rate = failRates.get(scene);
            int value = rate == null ? 0 : rate.get();
            result.put(scene.name(), new ChaosConfig(scene.name(), value > 0, value, null));
        }
        return result;
    }
}
