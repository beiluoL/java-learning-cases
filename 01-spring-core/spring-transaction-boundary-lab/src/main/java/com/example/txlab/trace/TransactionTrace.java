package com.example.txlab.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * 事务状态探针：把"当前线程的事务真实状态"读出来，并写进执行轨迹。
 *
 * <p>它解决的核心问题：学生只能看到 Controller 拿到 UnexpectedRollbackException，
 * 却看不到"业务方法 return 的那一刻事务是什么状态"。本探针在方法体内部逐点采样，
 * 从而把 {@code rollback-only} 被标记的时刻、真实 ROLLBACK 发生的时刻都变成可观测事实。</p>
 *
 * <p><b>重要语义提醒</b>：内层 REQUIRED 方法抛异常时，<em>不会</em>立即回滚整个物理事务。
 * 内层事务拦截器做的是"把共享事务标记为 rollback-only"（只是打一个标记），
 * 真正的 {@code Connection.rollback()} 由外层事务拥有者在完成阶段执行。
 * 本探针的 beforeCompletion / afterCompletion 回调就是用来证明这一点的。</p>
 */
@Component
public class TransactionTrace {

    private static final Logger log = LoggerFactory.getLogger(TransactionTrace.class);

    private final TraceRecorder recorder;

    public TransactionTrace(TraceRecorder recorder) {
        this.recorder = recorder;
    }

    /** 读取当前线程事务状态快照（不会抛异常） */
    public TxSnapshot snapshot() {
        boolean actualActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
        String txName = TransactionSynchronizationManager.getCurrentTransactionName();
        int resourceCount = TransactionSynchronizationManager.getResourceMap().size();

        Boolean newTx = null;
        Boolean rollbackOnly = null;
        if (actualActive) {
            try {
                // 只读：拿到当前 @Transactional 方法对应的 TransactionStatus
                TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
                newTx = status.isNewTransaction();
                rollbackOnly = status.isRollbackOnly();
            } catch (NoTransactionException ex) {
                // 方法体之外调用时读不到，保持 null
            }
        }
        return new TxSnapshot(actualActive, syncActive, txName, resourceCount, newTx, rollbackOnly);
    }

    /** 记录一条带事务状态的轨迹 */
    public TraceRecorder.Event mark(String phase, String message) {
        return recorder.record(phase, message, snapshot().toText());
    }

    /**
     * 在当前事务上注册一个 {@link TransactionSynchronization}，
     * 用于观察"事务完成阶段"（步骤 11-12）的真实顺序。
     *
     * <p>回调顺序（Spring 事务管理器在提交/回滚过程中触发）：
     * <pre>
     *   beforeCommit(readOnly)  → beforeCompletion() → [真正 COMMIT 或 ROLLBACK] → afterCompletion(status)
     * </pre>
     * 其中 {@code afterCompletion} 的 status 只有三种取值：
     * {@code STATUS_COMMITTED(0)} / {@code STATUS_ROLLED_BACK(1)} / {@code STATUS_UNKNOWN(2)}。
     * 只要看到 {@code STATUS_ROLLED_BACK}，就说明数据库层的回滚<em>已经执行完毕</em>。</p>
     *
     * @param owner        注册者名称，仅用于日志
     * @param statusProbe  在 afterCompletion 里执行的一次真实数据库读取，
     *                     用来证明"回滚后同一条连接里数据已复原"
     */
    public void registerOuterCompletionSynchronization(String owner, Supplier<String> statusProbe) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recorder.record("TX_SYNC",
                    "注意：当前线程没有激活的事务同步，说明调用没有经过事务代理（" + owner + "）");
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void beforeCommit(boolean readOnly) {
                recorder.record("TX_SYNC",
                        "【步骤11】beforeCommit(readOnly=" + readOnly + ")：事务拦截器已决定要走 commit 分支，同步回调先执行",
                        snapshot().toText());
            }

            @Override
            public void beforeCompletion() {
                recorder.record("TX_SYNC",
                        "【步骤11】beforeCompletion：外层事务进入完成阶段（此刻业务方法早已 return 完毕）",
                        snapshot().toText());
            }

            @Override
            public void afterCompletion(int status) {
                String statusText = switch (status) {
                    case TransactionSynchronization.STATUS_COMMITTED -> "COMMITTED(0)";
                    case TransactionSynchronization.STATUS_ROLLED_BACK -> "ROLLED_BACK(1)";
                    default -> "UNKNOWN(2)";
                };
                String probeResult;
                try {
                    probeResult = statusProbe.get();
                } catch (RuntimeException ex) {
                    probeResult = "探测失败：" + ex.getClass().getSimpleName() + " - " + ex.getMessage();
                }
                recorder.record("TX_SYNC",
                        "【步骤12】afterCompletion(status=" + statusText + ")：数据库层操作已执行完毕；"
                                + "此刻用同一条（已结束的）连接回读 → " + probeResult,
                        snapshot().toText());
                log.info("事务同步 afterCompletion: owner={}, status={}", owner, statusText);
            }
        });

        recorder.record("TX_SYNC",
                "已注册事务完成同步回调（用于捕获 beforeCompletion / afterCompletion 的真实时机）",
                snapshot().toText());
    }
}
