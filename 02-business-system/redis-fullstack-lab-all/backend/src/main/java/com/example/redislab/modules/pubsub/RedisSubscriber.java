package com.example.redislab.modules.pubsub;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * PUB/SUB 订阅者：监听指定 channel，收到消息后交给 MessageBroadcaster 推给前端。
 * 注意：PUB/SUB 是「即发即弃」——没有消费者时会丢消息，所以下面的 Stream 用来补可靠性。
 */
@Component
public class RedisSubscriber implements MessageListener {

    private final MessageBroadcaster broadcaster;

    public RedisSubscriber(MessageBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        broadcaster.broadcast(channel, body, "pubsub");
    }
}
