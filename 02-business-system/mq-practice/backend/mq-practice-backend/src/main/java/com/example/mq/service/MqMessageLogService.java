package com.example.mq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.mq.entity.MqMessageLog;

/**
 * 消息发送日志服务：生产者可靠投递的「账本」。
 */
public interface MqMessageLogService extends IService<MqMessageLog> {

    /** 发送前落库（SENDING），返回主键；失败不影响主流程（日志不该拖垮业务） */
    void saveSending(MqMessageLog messageLog);

    /** Broker 确认（Publisher Confirm ack=true） */
    void markConfirmed(String messageId);

    /** 路由失败（Publisher Return：到交换机但没有队列匹配） */
    void markReturned(String messageId, String replyText);

    /** 发送异常 / Broker nack */
    void markFailed(String messageId, String errorMsg);
}
