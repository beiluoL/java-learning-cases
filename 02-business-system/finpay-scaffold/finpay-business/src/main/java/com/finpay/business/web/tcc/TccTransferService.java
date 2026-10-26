package com.finpay.business.web.tcc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finpay.business.infrastructure.entity.BankTransaction;
import com.finpay.business.infrastructure.mapper.BankTransactionMapper;
import com.finpay.business.web.bank.AccountService;
import com.finpay.common.util.SnowflakeId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TCC 转账参与者（银行交易系统示例的核心一致性保障）。
 *
 * <p>三阶段：</p>
 * <ul>
 *   <li><b>Try</b>：转出方冻结金额，记录分支事务（状态 INIT）。此时资金未被真正扣减，仅锁定。</li>
 *   <li><b>Confirm</b>：释放冻结并真正扣减转出方、入账收款方，事务置 CONFIRMED。幂等，可重复执行。</li>
 *   <li><b>Cancel</b>：回滚冻结（余额加回），事务置 CANCELLED。用于任一参与者失败时的补偿。</li>
 * </ul>
 *
 * <p>真实分布式场景：每个分支是一个独立服务，由 TCC 协调器统一驱动 Try→Confirm/Cancel。
 * 此处在同一进程内演示完整语义，便于学习。</p>
 */
@Slf4j
@Service
public class TccTransferService {

    private final AccountService accountService;
    private final BankTransactionMapper txMapper;
    private final SnowflakeId snowflake = new SnowflakeId();

    public TccTransferService(AccountService accountService, BankTransactionMapper txMapper) {
        this.accountService = accountService;
        this.txMapper = txMapper;
    }

    /** TCC-Try */
    @Transactional
    public String tryTransfer(String from, String to, BigDecimal amount) {
        String branchId = snowflake.nextOrderNo("TX");
        accountService.tryFreeze(from, amount);
        BankTransaction tx = new BankTransaction();
        tx.setTxNo(branchId);
        tx.setFromAccount(from);
        tx.setToAccount(to);
        tx.setAmount(amount);
        tx.setType("TRANSFER");
        tx.setStatus("INIT");
        tx.setBranchId(branchId);
        tx.setCreateTime(LocalDateTime.now());
        txMapper.insert(tx);
        log.info("[TCC-Try] branch={} {} -> {} {}", branchId, from, to, amount);
        return branchId;
    }

    /** TCC-Confirm（幂等） */
    @Transactional
    public void confirmTransfer(String branchId) {
        BankTransaction tx = txMapper.selectOne(Wrappers.<BankTransaction>lambdaQuery().eq(BankTransaction::getBranchId, branchId));
        if (tx == null || "CONFIRMED".equals(tx.getStatus())) return;
        accountService.confirmDebit(tx.getFromAccount(), tx.getAmount());
        accountService.credit(tx.getToAccount(), tx.getAmount());
        tx.setStatus("CONFIRMED");
        txMapper.updateById(tx);
        log.info("[TCC-Confirm] branch={} 成功", branchId);
    }

    /** TCC-Cancel（幂等） */
    @Transactional
    public void cancelTransfer(String branchId) {
        BankTransaction tx = txMapper.selectOne(Wrappers.<BankTransaction>lambdaQuery().eq(BankTransaction::getBranchId, branchId));
        if (tx == null || "CANCELLED".equals(tx.getStatus())) return;
        accountService.cancelFreeze(tx.getFromAccount(), tx.getAmount());
        tx.setStatus("CANCELLED");
        txMapper.updateById(tx);
        log.info("[TCC-Cancel] branch={} 回滚", branchId);
    }
}
