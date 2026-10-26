package com.example.txlab.service;

import com.example.txlab.repository.OrderRepository;
import com.example.txlab.trace.TransactionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付服务（外层事务方法）—— 本实验的主角。
 *
 * <p>它做四件事：改订单状态、调审计、吞掉审计异常、返回 "SUCCESS"。
 * 从 Java 代码的角度看，这个方法<b>完全正常返回</b>了；
 * 但事务代理拿到返回值之后，还要做一件业务代码看不到的事：决定 COMMIT 还是 ROLLBACK。</p>
 *
 * <p>这就是「事务代理返回边界」：<b>方法 return 的时间点，早于事务真正提交/回滚的时间点。</b>
 * 在这两个时间点之间，事务代理发现共享事务已被标记 rollback-only，
 * 于是执行真正的回滚，并把「返回值丢掉、改成抛异常」——
 * 也就是 Controller 看到的 {@code UnexpectedRollbackException}。</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final AuditService auditService;
    private final TransactionTrace txTrace;

    public PaymentService(OrderRepository orderRepository, AuditService auditService, TransactionTrace txTrace) {
        this.orderRepository = orderRepository;
        this.auditService = auditService;
        this.txTrace = txTrace;
    }

    /**
     * 支付主流程。
     *
     * @param orderId 订单 ID
     * @return 业务方法自己的返回值（注意：这个值可能永远到不了 Controller）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String pay(Long orderId) {
        // 步骤 2：能进入方法体，就说明外层事务已经被代理开启
        txTrace.mark("TX_OUTER",
                "【步骤2】进入 PaymentService.pay()：外层事务已由事务代理开启"
                        + "（newTx=true 表示这条物理事务归本方法所有）");

        // 注册事务完成回调，用来把「步骤 11-12」的真实时机固定下来
        txTrace.registerOuterCompletionSynchronization(
                "PaymentService.pay",
                () -> "orders.status=" + orderRepository.findStatus(orderId));

        // 步骤 3：订单状态 UPDATE
        orderRepository.updateStatus(orderId, "PAID");
        txTrace.mark("TX_OUTER", "【步骤3】订单 UPDATE 已经执行，但事务还没有提交（此刻回滚，这行改动会消失）");

        try {
            // 步骤 4：调另一个 Spring Bean（走的是 AuditService 的代理，不是 this 自调用）
            txTrace.mark("TX_OUTER",
                    "【步骤4】准备调用 auditService.saveAudit()：目标是 AuditService 的 Spring 代理");
            auditService.saveAudit(orderId);
        } catch (RuntimeException e) {
            // 步骤 9：外层把异常吞掉
            log.warn("日志B：外层捕获审计异常，异常类型={}", e.getClass().getName());
            txTrace.mark("TX_OUTER",
                    "【步骤9】日志B：外层 catch 捕获 " + e.getClass().getSimpleName()
                            + "，异常被吞掉，方法体继续往下执行");
            // 步骤 8 的验证：此时共享事务已经被内层事务拦截器打过 rollback-only 标记
            txTrace.mark("ROLLBACK_ONLY",
                    "【步骤8验证】读共享事务状态：rollbackOnly=true 说明内层拦截器已经标记过，"
                            + "但此时数据库并没有回滚，订单 UPDATE 仍在未提交状态");
        }

        // 步骤 10：日志 A + return
        log.info("日志A：准备返回 SUCCESS");
        txTrace.mark("RETURN",
                "【步骤10】日志A 已打印，方法体即将 return \"SUCCESS\"（业务逻辑到这里就结束了）");

        return "SUCCESS";
    }
}
