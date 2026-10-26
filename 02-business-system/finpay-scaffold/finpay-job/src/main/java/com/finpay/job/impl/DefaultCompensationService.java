package com.finpay.job.impl;

import com.finpay.job.model.Mismatch;
import com.finpay.job.service.CompensationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认补偿服务。采用内存补偿队列（生产应换为数据库表 + 调度器）。
 * 策略：差错登记后延迟重试，最多 3 次，全部失败则升级告警。
 */
@Slf4j
@Service
public class DefaultCompensationService implements CompensationService {

    private final Map<String, Task> queue = new ConcurrentHashMap<>();
    private static final int MAX_RETRY = 3;

    @Override
    public void register(Mismatch mismatch) {
        Task t = new Task();
        t.mismatch = mismatch;
        t.retryCount = 0;
        t.nextRetry = LocalDateTime.now().plusMinutes(5);
        t.done = false;
        queue.put(mismatch.getOutTradeNo(), t);
        log.warn("[补偿] 登记差错 {} 类型={}", mismatch.getOutTradeNo(), mismatch.getType());
    }

    @Override
    public synchronized boolean retry(String outTradeNo) {
        Task t = queue.get(outTradeNo);
        if (t == null || t.done) return false;
        try {
            // 真实场景：调用对应补偿动作（补单/冲正/告警）
            boolean ok = doCompensate(t.mismatch);
            if (ok) {
                t.done = true;
                log.info("[补偿] 成功 {}", outTradeNo);
                return true;
            }
        } catch (Exception e) {
            log.error("[补偿] 执行异常 {}", outTradeNo, e);
        }
        t.retryCount++;
        if (t.retryCount >= MAX_RETRY) {
            t.done = true;
            log.error("[补偿] 重试耗尽，升级人工处理 {}", outTradeNo);
        } else {
            t.nextRetry = LocalDateTime.now().plusMinutes(5 * t.retryCount);
        }
        return false;
    }

    @Override
    public void retryDue() {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Task> e : queue.entrySet()) {
            Task t = e.getValue();
            if (!t.done && !t.nextRetry.isAfter(now)) {
                retry(e.getKey());
            }
        }
    }

    private boolean doCompensate(Mismatch m) {
        // 占位：返回 true 表示补偿成功；真实实现按差错类型调用业务补偿接口
        return true;
    }

    private static class Task {
        Mismatch mismatch;
        int retryCount;
        LocalDateTime nextRetry;
        boolean done;
    }
}
