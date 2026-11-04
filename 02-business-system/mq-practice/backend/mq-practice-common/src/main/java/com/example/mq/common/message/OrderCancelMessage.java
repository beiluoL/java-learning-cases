package com.example.mq.common.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 【场景二】订单超时取消消息（延迟消息的"载荷"）。
 * <p>
 * 注意：消息里<b>不带状态判断结果</b>。消费端必须回查数据库最新状态再决定是否取消，
 * 因为从「消息投递」到「消息被消费」这段时间里，用户完全可能已经付款。
 * 这也是延迟消息最典型的坑：<i>消息只是闹钟，不是决策依据</i>。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;

    private String orderNo;

    private Long productId;

    private Integer quantity;

    /** 下单时间，消费端可据此二次校验是否真的超时（防止 broker 时钟/网络造成的提前触发） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
