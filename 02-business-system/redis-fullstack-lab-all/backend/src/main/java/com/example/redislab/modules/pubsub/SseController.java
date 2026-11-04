package com.example.redislab.modules.pubsub;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 端点：前端（双窗口）连上后，Redis 收到的消息会通过 MessageBroadcaster 实时推过来。
 */
@RestController
@RequestMapping("/api/pubsub")
public class SseController {

    private final MessageBroadcaster broadcaster;

    public SseController(MessageBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping("/stream")
    public SseEmitter stream() {
        // 0L = 不设超时，连接持续到客户端断开（教学演示足够）
        SseEmitter emitter = new SseEmitter(0L);
        broadcaster.register(emitter);
        return emitter;
    }
}
