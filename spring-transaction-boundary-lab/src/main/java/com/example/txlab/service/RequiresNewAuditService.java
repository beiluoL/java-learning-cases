package com.example.txlab.service;

import com.example.txlab.repository.AuditRepository;
import com.example.txlab.trace.TransactionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对照场景 Test05：内层事务改为 {@link Propagation#REQUIRES_NEW}。
 *
 * <p>差异的本质：{@code REQUIRES_NEW} 不看外层事务，它<b>挂起</b>外层事务
 * （把外层连接解绑、暂存起来），再向连接池<b>借第二条连接</b>开启自己的物理事务。
 * 因此：</p>
 *
 * <ul>
 *   <li>内层异常只回滚内层那一条连接上的事务，外层连接完全不受影响；</li>
 *   <li>外层事务不会被标记 rollback-only，捕获异常后可以正常提交；</li>
 *   <li>内层写进去的审计日志会随内层回滚一起消失。</li>
 * </ul>
 *
 * <p>工程约束：因为是两条物理连接，连接池必须至少有 2 条可用连接，
 * 否则会出现"等待连接超时"的假死，而不是事务问题。</p>
 */
@Service
public class RequiresNewAuditService {

    private static final Logger log = LoggerFactory.getLogger(RequiresNewAuditService.class);

    private final AuditRepository auditRepository;
    private final TransactionTrace txTrace;

    public RequiresNewAuditService(AuditRepository auditRepository, TransactionTrace txTrace) {
        this.auditRepository = auditRepository;
        this.txTrace = txTrace;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAudit(Long orderId) {
        txTrace.mark("TX_INNER",
                "【Test05】进入 RequiresNewAuditService.saveAudit()：REQUIRES_NEW 挂起外层事务，"
                        + "借用第二条连接开启独立物理事务（newTx=true）");

        auditRepository.insert(orderId, "PAY_AUDIT_REQUIRES_NEW");
        txTrace.mark("TX_INNER", "【Test05】审计 INSERT 已执行在独立事务里，仍未提交");

        txTrace.mark("EXCEPTION", "【Test05】内层独立事务即将抛出 RuntimeException（只回滚它自己）");
        log.warn("RequiresNewAuditService 业务失败，准备抛出异常(orderId={})", orderId);
        throw new RuntimeException("模拟审计业务异常（REQUIRES_NEW 独立事务）");
    }
}
