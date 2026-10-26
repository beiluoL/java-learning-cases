package com.example.txlab.service;

import com.example.txlab.repository.AuditRepository;
import com.example.txlab.trace.TransactionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对照场景 Test04：内层<b>自己</b>把异常 catch 掉，不传播到它的事务代理。
 *
 * <p>结果差异的根因只有一句话：
 * <b>rollback-only 是"事务拦截器看到异常向外传播"才打的标记。</b>
 * 异常在方法体内部就被吃掉，拦截器根本不知道出过事，
 * 它看到的是"方法正常返回"，于是不做任何标记 —— 外层事务照样可以提交。</p>
 */
@Service
public class SelfHealingAuditService {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingAuditService.class);

    private final AuditRepository auditRepository;
    private final TransactionTrace txTrace;

    public SelfHealingAuditService(AuditRepository auditRepository, TransactionTrace txTrace) {
        this.auditRepository = auditRepository;
        this.txTrace = txTrace;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveAudit(Long orderId) {
        txTrace.mark("TX_INNER",
                "【Test04】进入 SelfHealingAuditService.saveAudit()：REQUIRED 加入外层事务（newTx=false）");

        auditRepository.insert(orderId, "PAY_AUDIT_SELF_CAUGHT");
        txTrace.mark("TX_INNER", "【Test04】审计 INSERT 已执行，事务仍未提交");

        try {
            throw new IllegalStateException("模拟审计业务异常（在内层方法内部被捕获）");
        } catch (RuntimeException e) {
            // 关键：异常在这里被彻底吃掉，不会再往外抛
            log.warn("【Test04】内层自己捕获异常，未向外传播，异常类型={}", e.getClass().getName());
            txTrace.mark("EXCEPTION",
                    "【Test04】异常在方法内部被 catch：事务拦截器完全感知不到，不会标记 rollback-only");
        }

        txTrace.mark("RETURN",
                "【Test04】内层方法正常返回给事务拦截器 → 拦截器认为一切正常，不做 rollback-only 标记");
    }
}
