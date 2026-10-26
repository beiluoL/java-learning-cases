package com.finpay.business.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 银行交易流水表。每一笔转账/扣款均落流水，用于对账与追溯。 */
@Data
@TableName("t_bank_transaction")
public class BankTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String txNo;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    /** 交易类型：TRANSFER / DEPOSIT / WITHDRAW */
    private String type;
    /** 状态：INIT / CONFIRMED / CANCELLED */
    private String status;
    /** TCC 分支事务标识，用于 Confirm/Cancel 关联 */
    private String branchId;
    private LocalDateTime createTime;
}
