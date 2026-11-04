package com.example.mq.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 「事务提交后」执行工具。
 * <p>
 * 为什么必须有它？考虑这段顺序：
 * <pre>
 *   ① 数据库：订单 status = UNPAID
 *   ② 投递延迟消息：30 分钟后取消订单
 * </pre>
 * 如果②在①提交前发出，而①最终回滚，消费端就会收到一条指向不存在订单的取消消息；
 * 如果②紧跟在①之后但事务尚未提交，消费端可能比提交更快地读到旧数据（读已提交隔离级别下读不到未提交值）。
 * 正确姿势：把「依赖事务结果的外部副作用」（发 MQ、写缓存、发通知）挪到 commit 之后。
 * <p>
 * 代价：commit 成功但回调抛异常时，消息就丢了 —— 所以需要「本地消息表 + 定时补偿」兜底，
 * 本项目用 {@code OrderTimeoutReconcileJob} 演示这个兜底思路。
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeRun(task);
                }
            });
        } else {
            // 没有事务上下文（如定时任务直接调用）→ 立即执行
            safeRun(task);
        }
    }

    private static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            // 绝不让回调异常污染已提交的事务
            log.error("[AFTER-COMMIT] 提交后动作执行失败（需靠补偿任务兜底）", e);
        }
    }
}
