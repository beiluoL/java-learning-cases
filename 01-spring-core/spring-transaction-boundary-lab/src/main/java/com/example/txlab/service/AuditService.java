package com.example.txlab.service;

import com.example.txlab.repository.AuditRepository;
import com.example.txlab.trace.TransactionTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计服务（内层事务方法）。
 *
 * <p>关键点：{@code REQUIRED} 表示"如果外层已经有事务，就加入它"。
 * 加入的结果是：<b>内层根本没有自己的物理事务</b>（不会有第二次 BEGIN），
 * 它与外层共用同一条 Connection、同一个事务对象。</p>
 *
 * <p>但"加入"不等于"没有自己的事务拦截器"。{@code saveAudit} 抛出的
 * {@link RuntimeException} 会先被<b>内层事务拦截器</b>看见：
 * 它按默认规则判定"该回滚"，于是把这个共享事务标记为 {@code rollback-only}
 * （只是打标记，不执行 {@code Connection.rollback()}），然后把异常原样抛出。</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;
    private final TransactionTrace txTrace;

    public AuditService(AuditRepository auditRepository, TransactionTrace txTrace) {
        this.auditRepository = auditRepository;
        this.txTrace = txTrace;
    }

    /**
     * 写审计日志，然后抛出真实的运行时异常。
     *
     * <p>注意：这里没有 {@code TransactionAspectSupport.setRollbackOnly()}，
     * rollback-only 是内层事务拦截器<em>因为异常向外传播</em>而自动标记的。</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveAudit(Long orderId) {
        txTrace.mark("TX_INNER",
                "【步骤4/5】进入 AuditService.saveAudit()：内层事务代理已加入外层事务"
                        + "（newTx=false 表示没有新开物理事务）");

        auditRepository.insert(orderId, "PAY_AUDIT");
        txTrace.mark("TX_INNER", "【步骤5】审计 INSERT 已执行，事务仍未提交（同一条连接里能看到，其他连接看不到）");

        txTrace.mark("EXCEPTION", "【步骤6】内层方法即将抛出 RuntimeException（真实业务异常，不是伪造）");
        log.warn("AuditService.saveAudit 业务校验失败，准备抛出 RuntimeException(orderId={})", orderId);
        throw new RuntimeException("模拟审计业务异常");
    }
}
