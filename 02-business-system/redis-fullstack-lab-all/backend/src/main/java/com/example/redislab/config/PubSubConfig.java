package com.example.redislab.config;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.modules.pubsub.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 注册 PUB/SUB 监听器容器：把 RedisSubscriber 绑到 pubsub:demo channel。
 * 收到频道消息时由 RedisSubscriber.onMessage 处理。
 */
@Configuration
public class PubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisKeys.pubsubChannel()));
        return container;
    }
}
