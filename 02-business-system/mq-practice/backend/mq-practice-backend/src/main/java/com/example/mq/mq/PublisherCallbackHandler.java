package com.example.mq.mq;

import com.example.mq.service.MqMessageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产者回调统一处理：把 broker 的「确认/退回」结果写回消息日志表。
 *
 * <h3>两个回调的语义必须分清（面试高频）</h3>
 * <table border="1">
 *   <tr><th>回调</th><th>触发时机</th><th>能证明什么</th></tr>
 *   <tr><td>ConfirmCallback(ack=true)</td><td>消息到达 Exchange 并被 broker 接收</td>
 *       <td>只证明"到交换机了"，<b>不证明有队列收到</b></td></tr>
 *   <tr><td>ReturnsCallback</td><td>到达 Exchange，但没有任何队列匹配 routingKey</td>
 *       <td>路由失败。必须同时满足 publisher-returns=true + mandatory=true</td></tr>
 * </table>
 * 所以工业级写法是：confirm=true 且 未触发 return，才算真正投递成功。
 * 只监听 confirm 就宣布"消息不丢"，是常见的假安全。
 */
@Slf4j
@Component
public class PublisherCallbackHandler implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private final MqMessageLogService messageLogService;

    public PublisherCallbackHandler(MqMessageLogService messageLogService) {
        this.messageLogService = messageLogService;
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        String messageId = correlationData == null ? null : correlationData.getId();
        if (messageId == null) {
            log.warn("[MQ-CONFIRM] 收到无 CorrelationData 的确认，无法关联业务消息");
            return;
        }
        if (ack) {
            messageLogService.markConfirmed(messageId);
        } else {
            // 常见原因：交换机不存在、队列被删除、磁盘满、集群节点不可用
            messageLogService.markFailed(messageId, "broker nack: " + cause);
        }
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        String messageId = returned.getMessage() == null
                ? null
                : returned.getMessage().getMessageProperties().getMessageId();
        String detail = String.format("exchange=%s routingKey=%s replyCode=%d replyText=%s",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText());
        if (messageId == null) {
            log.error("[MQ-RETURN] 无法从消息属性取到 messageId，详情：{}", detail);
            return;
        }
        messageLogService.markReturned(messageId, detail);
    }
}
