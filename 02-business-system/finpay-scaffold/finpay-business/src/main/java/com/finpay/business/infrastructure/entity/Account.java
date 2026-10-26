package com.finpay.business.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.finpay.business.infrastructure.handler.SensitiveTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 银行账户表。演示「银行交易系统」示例的核心实体，余额变更通过 TCC 保证一致性。
 * 银行卡号使用加密转换器 {@code SensitiveConverter} 落库加密。
 */
@Data
@TableName("t_account")
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountNo;
    /** 持卡人（加密存储） */
    @TableField(typeHandler = SensitiveTypeHandler.class)
    private String holderName;
    /** 银行卡号（加密存储） */
    @TableField(typeHandler = SensitiveTypeHandler.class)
    private String cardNo;
    private BigDecimal balance;
    /** TCC 预留（冻结）金额 */
    private BigDecimal frozen;
    private LocalDateTime createTime;
}
