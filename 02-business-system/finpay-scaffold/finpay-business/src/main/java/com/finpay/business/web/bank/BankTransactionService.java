package com.finpay.business.web.bank;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finpay.business.infrastructure.entity.BankTransaction;
import com.finpay.business.infrastructure.mapper.BankTransactionMapper;
import com.finpay.business.web.tcc.TccTransferService;
import com.finpay.common.exception.BizException;
import com.finpay.common.result.ErrorCode;
import com.finpay.payment.core.model.enums.PayStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 银行交易服务。编排 TCC 三阶段完成一笔转账：Try → (Confirm | Cancel)。
 * 任一阶段异常均触发 Cancel 补偿，保证资金最终一致。
 */
@Slf4j
@Service
public class BankTransactionService {

    private final TccTransferService tcc;
    private final BankTransactionMapper txMapper;

    public BankTransactionService(TccTransferService tcc, BankTransactionMapper txMapper) {
        this.tcc = tcc;
        this.txMapper = txMapper;
    }

    /** 执行一笔转账（演示 TCC 编排） */
    public String transfer(String from, String to, BigDecimal amount) {
        String branchId = tcc.tryTransfer(from, to, amount);
        try {
            tcc.confirmTransfer(branchId);
            return branchId;
        } catch (Exception e) {
            log.warn("[TCC] Confirm 失败，触发 Cancel branch={}", branchId, e);
            tcc.cancelTransfer(branchId);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "转账失败已回滚：" + e.getMessage());
        }
    }

    public List<BankTransaction> list() {
        return txMapper.selectList(Wrappers.<BankTransaction>lambdaQuery()
                .orderByDesc(BankTransaction::getCreateTime).last("LIMIT 100"));
    }
}
