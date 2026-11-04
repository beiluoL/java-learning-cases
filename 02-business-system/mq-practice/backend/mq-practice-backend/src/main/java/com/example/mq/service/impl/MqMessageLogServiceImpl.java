package com.example.mq.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.mq.entity.MqMessageLog;
import com.example.mq.mapper.MqMessageLogMapper;
import com.example.mq.service.MqMessageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息日志实现：所有状态变更都是「条件更新」，
 * 避免 confirm 回调（异步线程）覆盖掉更晚发生的 FAILED 状态。
 */
@Slf4j
@Service
public class MqMessageLogServiceImpl extends ServiceImpl<MqMessageLogMapper, MqMessageLog>
        implements MqMessageLogService {

    /**
     * <b>REQUIRES_NEW 是这里的关键</b>：发送日志必须「独立事务立刻提交」。
     * <p>
     * 否则会出现一个极难排查的现象：调用方在事务里（或 afterCommit 里）发消息，
     * 日志行还没提交，confirm 回调（另一个线程 + 另一个连接）回来后
     * {@code UPDATE ... WHERE message_id = ? AND status = 'SENDING'} 匹配 0 行，
     * 这条消息就永远停在 SENDING —— 看起来像"消息没被确认"，其实只是账本写得太晚。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveSending(MqMessageLog entity) {
        try {
            entity.setStatus(MqMessageLog.STATUS_SENDING);
            if (entity.getRetryCount() == null) {
                entity.setRetryCount(0);
            }
            save(entity);
        } catch (Exception e) {
            // 日志失败不能阻断发消息 —— 账本丢一条是可接受的，业务发不出去是不可接受的
            log.warn("[MQ-LOG] 保存发送日志失败 messageId={} cause={}", entity.getMessageId(), e.getMessage());
        }
    }

    @Override
    public void markConfirmed(String messageId) {
        boolean updated = lambdaUpdate()
                .eq(MqMessageLog::getMessageId, messageId)
                .eq(MqMessageLog::getStatus, MqMessageLog.STATUS_SENDING)
                .set(MqMessageLog::getStatus, MqMessageLog.STATUS_CONFIRMED)
                .update();
        if (updated) {
            log.debug("[MQ-CONFIRM] messageId={} 已确认落盘", messageId);
        }
    }

    @Override
    public void markReturned(String messageId, String replyText) {
        boolean updated = lambdaUpdate()
                .eq(MqMessageLog::getMessageId, messageId)
                .set(MqMessageLog::getStatus, MqMessageLog.STATUS_RETURNED)
                .set(MqMessageLog::getErrorMsg, truncate(replyText))
                .update();
        log.error("[MQ-RETURN] 消息到达交换机但路由失败，已记录 messageId={} reply={} updated={}",
                messageId, replyText, updated);
    }

    @Override
    public void markFailed(String messageId, String errorMsg) {
        lambdaUpdate()
                .eq(MqMessageLog::getMessageId, messageId)
                .set(MqMessageLog::getStatus, MqMessageLog.STATUS_FAILED)
                .set(MqMessageLog::getErrorMsg, truncate(errorMsg))
                .update();
        log.error("[MQ-FAIL] 消息发送失败 messageId={} cause={}", messageId, errorMsg);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
