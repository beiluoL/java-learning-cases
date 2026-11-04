package com.example.redislab.modules.pubsub;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 广播器：持有所有前端建立的 SseEmitter，把 Redis 收到的消息实时推送给每个前端。
 * 配合 PUB/SUB 订阅者使用，实现「A 发 B 收」的实时双窗口效果。
 */
@Component
public class MessageBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    public void broadcast(String channel, String body, String via) {
        Map<String, Object> payload = Map.of(
                "channel", channel,
                "body", body,
                "via", via);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("msg").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
