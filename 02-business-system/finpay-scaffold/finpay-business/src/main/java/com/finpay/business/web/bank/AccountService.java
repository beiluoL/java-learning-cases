package com.finpay.business.web.bank;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.finpay.business.infrastructure.entity.Account;
import com.finpay.business.infrastructure.mapper.AccountMapper;
import com.finpay.common.exception.BizException;
import com.finpay.common.result.ErrorCode;
import com.finpay.common.util.SnowflakeId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 银行账户服务（银行交易系统示例）。余额变更通过对账/冻结字段保证 TCC 一致性。
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final SnowflakeId snowflake = new SnowflakeId();

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Transactional
    public Account open(String holderName, String cardNo, BigDecimal initBalance) {
        Account a = new Account();
        a.setAccountNo(snowflake.nextOrderNo("AC"));
        a.setHolderName(holderName);
        a.setCardNo(cardNo);
        a.setBalance(initBalance);
        a.setFrozen(BigDecimal.ZERO);
        a.setCreateTime(LocalDateTime.now());
        accountMapper.insert(a);
        return a;
    }

    public Account get(String accountNo) {
        return accountMapper.selectOne(Wrappers.<Account>lambdaQuery().eq(Account::getAccountNo, accountNo));
    }

    public List<Account> list() {
        return accountMapper.selectList(null);
    }

    /** TCC-Try：冻结转出金额（余额减少、冻结增加），不足则报错 */
    @Transactional
    public void tryFreeze(String accountNo, BigDecimal amount) {
        int rows = accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getAccountNo, accountNo)
                .ge(Account::getBalance, amount)
                .setSql("balance = balance - " + amount.doubleValue())
                .setSql("frozen = frozen + " + amount.doubleValue()));
        if (rows == 0) throw new BizException(ErrorCode.BALANCE_NOT_ENOUGH, accountNo);
    }

    /** TCC-Confirm：释放冻结（转账方出账完成） */
    @Transactional
    public void confirmDebit(String accountNo, BigDecimal amount) {
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getAccountNo, accountNo)
                .setSql("frozen = frozen - " + amount.doubleValue()));
    }

    /** 入账（收款方） */
    @Transactional
    public void credit(String accountNo, BigDecimal amount) {
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getAccountNo, accountNo)
                .setSql("balance = balance + " + amount.doubleValue()));
    }

    /** TCC-Cancel：回滚冻结（余额加回、冻结减少） */
    @Transactional
    public void cancelFreeze(String accountNo, BigDecimal amount) {
        accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                .eq(Account::getAccountNo, accountNo)
                .setSql("balance = balance + " + amount.doubleValue())
                .setSql("frozen = frozen - " + amount.doubleValue()));
    }
}
