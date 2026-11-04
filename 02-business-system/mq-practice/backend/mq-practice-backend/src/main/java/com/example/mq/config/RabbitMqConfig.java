package com.example.mq.config;

import com.example.mq.common.mq.MqConstants;
import com.example.mq.mq.PublisherCallbackHandler;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 核心配置：拓扑声明 + 序列化 + 生产者确认 + 消费者手动 ACK。
 *
 * <h3>为什么用代码声明拓扑而不是只靠控制台手工建？</h3>
 * 因为「环境漂移」是 MQ 事故重灾区：测试环境有 queue，生产忘了建；或者 queue 参数
 * （TTL / DLX）不一致导致死信不生效。代码声明 + 幂等重复声明（参数相同则忽略）
 * 可以让拓扑随代码版本一起走 CI，是主流开源项目的标准做法。
 *
 * <h3>可靠性三件套</h3>
 * <ol>
 *   <li><b>publisher-confirm</b>：消息到达 Exchange 后 broker 回调 ack/nack —— 只能证明"到交换机"；</li>
 *   <li><b>publisher-return + mandatory</b>：到达 Exchange 但路由不到队列时回调 —— 补上 confirm 无法覆盖的漏洞；</li>
 *   <li><b>manual ack</b>：消费者处理成功才 basicAck，异常走重试队列或死信 —— 保证"不丢"。</li>
 * </ol>
 * 三者合一，才叫"消息不丢"，只开 confirm 是最常见的半吊子配置。
 */
@Configuration
public class RabbitMqConfig {

    /** 手动 ACK 监听容器工厂 Bean 名，所有 @RabbitListener 都指定它 */
    public static final String MANUAL_ACK_FACTORY = "manualAckContainerFactory";

    private final MqProperties properties;

    public RabbitMqConfig(MqProperties properties) {
        this.properties = properties;
    }

    /* ==================================================================================
     *  一、消息序列化：JSON（不要用 Java 原生序列化，跨语言/可读性/安全性都差）
     * ================================================================================== */

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        // 安全加固：只信任本项目消息体所在的包，防止反序列化攻击（CVE 常见形态）
        classMapper.setTrustedPackages("com.example.mq.common.message", "java.util", "java.lang");
        converter.setClassMapper(classMapper);
        // 让每条消息自动带 messageId，便于在控制台/日志里串联
        converter.setCreateMessageIds(true);
        return converter;
    }

    /* ==================================================================================
     *  二、RabbitTemplate：生产者端（确认 + 退回回调）
     * ================================================================================== */

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter,
                                         PublisherCallbackHandler callbackHandler) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        // mandatory=true 才会触发 ReturnsCallback（消息到交换机但没路由到队列）
        template.setMandatory(true);
        template.setConfirmCallback(callbackHandler);
        template.setReturnsCallback(callbackHandler);
        // 发送超时：避免 broker 假死时业务线程被长期挂住
        template.setReplyTimeout(5000);
        return template;
    }

    /* ==================================================================================
     *  三、监听容器工厂：手动 ACK + 预取 + 并发
     * ================================================================================== */

    @Bean(MANUAL_ACK_FACTORY)
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // 先把 application.yml 里的 spring.rabbitmq.listener.simple.* 全部应用进来
        configurer.configure(factory, connectionFactory);

        // 手动 ACK：方法正常返回后由我们显式 ack；抛异常则进入重试/死信逻辑
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 预取 5：单个消费者最多缓存 5 条未确认消息。太大→某个消费者堆积；太小→吞吐低
        factory.setPrefetchCount(5);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        // 拒绝消息不再重回原队列（否则会无限循环），交给死信交换机
        factory.setDefaultRequeueRejected(false);
        // broker 上队列尚未声明时不要让应用启动失败（首次部署顺序不确定）
        factory.setMissingQueuesFatal(false);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }

    /* ==================================================================================
     *  四、拓扑声明（Declarables 一次性声明，RabbitAdmin 会在连接建立后自动创建）
     * ================================================================================== */

    @Bean
    public Declarables mqTopologyDeclarables() {
        List<Declarable> items = new ArrayList<>();

        /* ---------- 公共：死信交换机 + 死信队列 ---------- */
        items.add(durableDirect(MqConstants.DLX_EXCHANGE));
        items.add(QueueBuilder.durable(MqConstants.DLQ_QUEUE).build());
        items.add(bindDirect(MqConstants.DLQ_QUEUE, MqConstants.DLX_EXCHANGE, MqConstants.DLQ_ROUTING_KEY));

        /* ---------- 公共：重试交换机（routingKey = 目标业务队列名） ---------- */
        items.add(durableDirect(MqConstants.RETRY_EXCHANGE));

        /* ================= 场景一：订单创建（削峰填谷） ================= */
        items.add(durableDirect(MqConstants.ORDER_EXCHANGE));
        items.add(businessQueue(MqConstants.ORDER_CREATE_QUEUE));
        items.add(bindDirect(MqConstants.ORDER_CREATE_QUEUE, MqConstants.ORDER_EXCHANGE,
                MqConstants.ORDER_CREATE_ROUTING_KEY));
        addRetryQueues(items, MqConstants.ORDER_CREATE_QUEUE);

        /* ================= 场景二：延迟 → 超时取消 ================= */
        // 延迟阶段：这两个队列都没有消费者，只负责"扣住"消息，TTL 到期后死信到 cancel 交换机。
        // 按延迟档位拆成两个队列 —— 这是 DLX+TTL 方案规避「队头阻塞」的标准做法。
        items.add(durableDirect(MqConstants.ORDER_DELAY_EXCHANGE));
        items.add(QueueBuilder.durable(MqConstants.ORDER_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.ORDER_CANCEL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqConstants.ORDER_CANCEL_ROUTING_KEY)
                .build());
        items.add(bindDirect(MqConstants.ORDER_DELAY_QUEUE, MqConstants.ORDER_DELAY_EXCHANGE,
                MqConstants.ORDER_DELAY_ROUTING_KEY));
        // 短延迟档：演示用（<=60s），避免被 30 分钟的长延迟消息堵住
        items.add(QueueBuilder.durable(MqConstants.ORDER_DELAY_SHORT_QUEUE)
                .withArgument("x-dead-letter-exchange", MqConstants.ORDER_CANCEL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqConstants.ORDER_CANCEL_ROUTING_KEY)
                .build());
        items.add(bindDirect(MqConstants.ORDER_DELAY_SHORT_QUEUE, MqConstants.ORDER_DELAY_EXCHANGE,
                MqConstants.ORDER_DELAY_SHORT_ROUTING_KEY));

        // 真正的消费阶段
        items.add(durableDirect(MqConstants.ORDER_CANCEL_EXCHANGE));
        items.add(businessQueue(MqConstants.ORDER_CANCEL_QUEUE));
        items.add(bindDirect(MqConstants.ORDER_CANCEL_QUEUE, MqConstants.ORDER_CANCEL_EXCHANGE,
                MqConstants.ORDER_CANCEL_ROUTING_KEY));
        addRetryQueues(items, MqConstants.ORDER_CANCEL_QUEUE);

        /* 可选：延迟插件交换机（mq.practice.delay-mode=plugin 时才声明） */
        if ("plugin".equalsIgnoreCase(properties.getDelayMode())) {
            Map<String, Object> args = new HashMap<>();
            args.put("x-delayed-type", "direct");
            items.add(new CustomExchange(MqConstants.ORDER_DELAY_PLUGIN_EXCHANGE,
                    "x-delayed-message", true, false, args));
            items.add(QueueBuilder.durable(MqConstants.ORDER_DELAY_PLUGIN_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.ORDER_CANCEL_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.ORDER_CANCEL_ROUTING_KEY)
                    .build());
            items.add(bindDirect(MqConstants.ORDER_DELAY_PLUGIN_QUEUE,
                    MqConstants.ORDER_DELAY_PLUGIN_EXCHANGE, MqConstants.ORDER_DELAY_ROUTING_KEY));
        }

        /* ================= 场景三：商品变更发布订阅（topic） ================= */
        items.add(new TopicExchange(MqConstants.PRODUCT_EXCHANGE, true, false));
        items.add(businessQueue(MqConstants.PRODUCT_CACHE_QUEUE));
        items.add(businessQueue(MqConstants.PRODUCT_SEARCH_QUEUE));
        // 同一个 routingKey 绑定两个队列 —— 这就是 fanout 语义在 topic 上的实现
        items.add(bindTopic(MqConstants.PRODUCT_CACHE_QUEUE, MqConstants.PRODUCT_EXCHANGE,
                MqConstants.PRODUCT_ROUTING_KEY_PATTERN));
        items.add(bindTopic(MqConstants.PRODUCT_SEARCH_QUEUE, MqConstants.PRODUCT_EXCHANGE,
                MqConstants.PRODUCT_ROUTING_KEY_PATTERN));
        addRetryQueues(items, MqConstants.PRODUCT_CACHE_QUEUE);
        addRetryQueues(items, MqConstants.PRODUCT_SEARCH_QUEUE);

        return new Declarables(items);
    }

    /* ------------------------------- 小工具 ------------------------------- */

    /**
     * 业务队列统一带上死信参数：消费失败且无需重试（或重试次数耗尽）时，
     * 消息会被投到 mq.dlx.exchange，最终落到 mq.dlq.queue，绝不"凭空消失"。
     */
    private Queue businessQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", MqConstants.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MqConstants.DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * 为一个业务队列配套声明「重试队列」。
     * <pre>
     *   消费失败 ──▶ mq.retry.exchange --(rk=业务队列名)--> &lt;业务队列&gt;.retry.queue
     *                     （无消费者，只等 TTL）
     *                              │ TTL 到期（默认 5s）
     *                              ▼  x-dead-letter-exchange = ""（默认交换机）
     *                                 x-dead-letter-routing-key = 业务队列名
     *                          ──▶ 原业务队列（精准回投，不会因为 topic 广播而多投一份）
     * </pre>
     * 把重试队列的死信投递到<b>默认交换机</b>（名字为空串）是这里的关键技巧：
     * 默认交换机会把消息路由到「名字 == routingKey」的那一个队列，
     * 因此可以直接回到指定队列，而不是回到业务交换机再被扇出给所有订阅者。
     */
    private void addRetryQueues(List<Declarable> items, String businessQueueName) {
        String retryQueueName = businessQueueName.replace(".queue", ".retry.queue");
        items.add(QueueBuilder.durable(retryQueueName)
                .withArgument("x-message-ttl", (int) properties.getRetryDelayMs())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", businessQueueName)
                .build());
        // 用「业务队列名」作为 routingKey 绑定，消费端回投时可直接用 consumerQueue
        items.add(bindDirect(retryQueueName, MqConstants.RETRY_EXCHANGE, businessQueueName));
    }

    private DirectExchange durableDirect(String name) {
        return new DirectExchange(name, true, false);
    }

    private Binding bindDirect(String queue, String exchange, String routingKey) {
        return BindingBuilder.bind(new Queue(queue)).to(new DirectExchange(exchange)).with(routingKey);
    }

    private Binding bindTopic(String queue, String exchange, String routingKey) {
        return BindingBuilder.bind(new Queue(queue)).to(new TopicExchange(exchange)).with(routingKey);
    }
}
