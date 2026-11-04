package com.example.mq.common.exception;

import java.io.Serial;

/**
 * 「不可重试」的业务失败。
 * <p>
 * 与普通异常的差别决定消息去向：
 * <ul>
 *   <li>业务失败（库存不足、订单状态不对）→ 重试一万次也不会成功，应直接进死信队列等人工/补偿处理；</li>
 *   <li>系统异常（DB 超时、Redis 抖动、下游 500）→ 重试有意义，走重试队列退避重投。</li>
 * </ul>
 * 这条区分是消费端设计里最容易漏、也最容易造成「消息风暴」的地方：
 * 把不可重试的异常当可重试，等于用消息队列攻击自己的数据库。
 */
public class BusinessRejectException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRejectException(String message) {
        super(message);
    }

    public BusinessRejectException(String message, Throwable cause) {
        super(message, cause);
    }
}
