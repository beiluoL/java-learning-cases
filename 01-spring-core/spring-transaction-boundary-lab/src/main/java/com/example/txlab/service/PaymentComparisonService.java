package com.example.txlab.service;

import com.example.txlab.repository.OrderRepository;
import com.example.txlab.trace.TransactionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 两个对照场景的外层业务方法。
 *
 * <p>它们的外层事务定义与 {@link PaymentService#pay(Long)} 完全一致（REQUIRED），
 * 唯一差别是"内层怎么处理异常"，用来隔离变量、单独观察某一种情形。</p>
 */
@Service
public class PaymentComparisonService {

    private static final Logger log = LoggerFactory.getLogger(PaymentComparisonService.class);

    private final OrderRepository orderRepository;
    private final SelfHealingAuditService selfHealingAuditService;
    private final RequiresNewAuditService requiresNewAuditService;
    private final TransactionTrace txTrace;

    public PaymentComparisonService(OrderRepository orderRepository,
                                    SelfHealingAuditService selfHealingAuditService,
                                    RequiresNewAuditService requiresNewAuditService,
                                    TransactionTrace txTrace) {
        this.orderRepository = orderRepository;
        this.selfHealingAuditService = selfHealingAuditService;
        this.requiresNewAuditService = requiresNewAuditService;
        this.txTrace = txTrace;
    }

    /**
     * Test04：内层自己吞掉异常 → 外层事务应当正常提交。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String payWithSelfCaughtAudit(Long orderId) {
        txTrace.mark("TX_OUTER", "【Test04】外层事务开启（newTx=true）");
        txTrace.registerOuterCompletionSynchronization(
                "PaymentComparisonService.payWithSelfCaughtAudit",
                () -> "orders.status=" + orderRepository.findStatus(orderId));

        orderRepository.updateStatus(orderId, "PAID");
        txTrace.mark("TX_OUTER", "【Test04】订单 UPDATE 已执行，未提交");

        // 内层方法内部会吞掉异常，正常返回
        selfHealingAuditService.saveAudit(orderId);

        txTrace.mark("ROLLBACK_ONLY",
                "【Test04】内层正常返回后读共享事务状态：rollbackOnly=false（没有任何回滚标记）");

        log.info("日志A：准备返回 SUCCESS");
        txTrace.mark("RETURN", "【Test04】日志A 已打印，方法体即将 return \"SUCCESS\"");
        return "SUCCESS";
    }

    /**
     * Test05：内层 REQUIRES_NEW 独立事务回滚 → 外层捕获异常后仍然可以提交。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String payWithRequiresNewAudit(Long orderId) {
        txTrace.mark("TX_OUTER", "【Test05】外层事务开启（newTx=true）");
        txTrace.registerOuterCompletionSynchronization(
                "PaymentComparisonService.payWithRequiresNewAudit",
                () -> "orders.status=" + orderRepository.findStatus(orderId));

        orderRepository.updateStatus(orderId, "PAID");
        txTrace.mark("TX_OUTER", "【Test05】订单 UPDATE 已执行，未提交");

        try {
            // 内层 REQUIRES_NEW：挂起外层事务，自己借第二条连接开新事务
            requiresNewAuditService.saveAudit(orderId);
        } catch (RuntimeException e) {
            log.warn("日志B：外层捕获内层 REQUIRES_NEW 异常，异常类型={}", e.getClass().getName());
            txTrace.mark("TX_OUTER",
                    "【Test05】日志B：外层捕获 " + e.getClass().getSimpleName()
                            + "，内层已回滚它自己的物理事务，外层事务未被标记 rollback-only");
        }

        txTrace.mark("ROLLBACK_ONLY",
                "【Test05】内层异常返回后读共享事务状态：rollbackOnly=false（内层独立事务不影响外层标记）");

        log.info("日志A：准备返回 SUCCESS");
        txTrace.mark("RETURN", "【Test05】日志A 已打印，方法体即将 return \"SUCCESS\"");
        return "SUCCESS";
    }
}
