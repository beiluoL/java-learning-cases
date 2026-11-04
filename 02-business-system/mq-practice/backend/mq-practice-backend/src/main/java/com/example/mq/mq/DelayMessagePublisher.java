package com.example.mq.mq;

import com.example.mq.common.message.OrderCancelMessage;

/**
 * 延迟消息发送抽象。
 * <p>
 * 为什么抽这一层？因为「延迟」在 RabbitMQ 里没有原生标准答案，两种主流实现各有取舍：
 * <table border="1">
 *   <tr><th>方案</th><th>优点</th><th>缺点</th></tr>
 *   <tr><td>DLX + TTL（{@link DlxDelayMessagePublisher}）</td>
 *       <td>零插件，任何 RabbitMQ 都能跑</td>
 *       <td>同一队列内不同 TTL 会「队头阻塞」：先入队的 30 分钟消息会挡住后入队的 10 秒消息</td></tr>
 *   <tr><td>x-delayed-message 插件（{@link PluginDelayMessagePublisher}）</td>
 *       <td>延迟精度高、支持任意 TTL 混用</td>
 *       <td>需要运维安装 rabbitmq_delayed_message_exchange 插件</td></tr>
 * </table>
 * 通过 {@code mq.practice.delay-mode} 切换，业务代码只依赖接口 —— 这就是"抽象设计"的实际收益。
 */
public interface DelayMessagePublisher {

    /**
     * 投递一条延迟消息。
     *
     * @param payload      消息体
     * @param delaySeconds 延迟秒数
     * @return messageId
     */
    String publish(OrderCancelMessage payload, int delaySeconds);
}
