package com.finpay.business.web.bank;

import com.finpay.common.result.Result;
import com.finpay.security.audit.AuditLog;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 银行交易系统示例 Controller。涵盖开户、转账（TCC）、余额查询、流水查询。
 */
@RestController
@RequestMapping("/api/bank")
@CrossOrigin
public class BankTransactionController {

    private final AccountService accountService;
    private final BankTransactionService txService;

    public BankTransactionController(AccountService accountService, BankTransactionService txService) {
        this.accountService = accountService;
        this.txService = txService;
    }

    @PostMapping("/open")
    @AuditLog(action = "OPEN_ACCOUNT", resource = "ACCOUNT")
    public Result<?> open(@RequestParam String holder, @RequestParam String cardNo,
                          @RequestParam(defaultValue = "0") BigDecimal init) {
        return Result.success(accountService.open(holder, cardNo, init));
    }

    @PostMapping("/transfer")
    @AuditLog(action = "TRANSFER", resource = "ACCOUNT", logParams = false)
    public Result<?> transfer(@RequestParam String from, @RequestParam String to,
                              @RequestParam BigDecimal amount) {
        return Result.success(txService.transfer(from, to, amount));
    }

    @GetMapping("/accounts")
    public Result<?> accounts() {
        return Result.success(accountService.list());
    }

    @GetMapping("/balance")
    public Result<?> balance(@RequestParam String accountNo) {
        return Result.success(accountService.get(accountNo));
    }

    @GetMapping("/transactions")
    public Result<?> transactions() {
        List<?> list = txService.list();
        return Result.success(list);
    }
}
