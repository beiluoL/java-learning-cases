package com.example.redislab.modules.pubsub;

import com.example.redislab.common.R;
import com.example.redislab.common.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景5：发布订阅。
 * POST /api/pubsub/publish  发消息（同时写 PUB/SUB 频道 + Stream 队列）
 * GET  /api/pubsub/consume  用消费组读取 Stream（演示可靠、不丢、可 ack）
 *
 * PUB/SUB 实时但易丢；Stream 带消费组、可重放、可 ack，适合做可靠消息队列。
 * 实时推送前端的通道是 SSE（/api/pubsub/stream），由 RedisSubscriber 触发广播。
 */
@RestController
@RequestMapping("/api/pubsub")
public class PubSubController {

    private static final String GROUP = "cg";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PubSubController(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 启动时创建消费组（已存在则忽略） */
    @PostConstruct
    public void initGroup() {
        try {
            stringRedisTemplate.opsForStream().createGroup(RedisKeys.streamChannel(), GROUP);
        } catch (Exception ignored) {
            // 消费组已存在，忽略
        }
    }

    @PostMapping("/publish")
    public R<Void> publish(@RequestParam String from, @RequestParam String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("text", text);
        payload.put("ts", System.currentTimeMillis());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 1) PUB/SUB：实时广播（无消费者会丢）
        stringRedisTemplate.convertAndSend(RedisKeys.pubsubChannel(), json);

        // 2) Stream：可靠消息，带消费组，可被消费组重新读取/ack
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("from", from);
        record.put("text", text);
        record.put("ts", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.opsForStream().add(RedisKeys.streamChannel(), record);

        return R.ok();
    }

    /** 用消费组 c1 读取新消息并 ack（演示 Stream 可靠消费） */
    @GetMapping("/consume")
    public R<List<Object>> consume() {
        List<MapRecord<String, Object, Object>> records =
                stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP, "c1"),
                        StreamReadOptions.empty().count(10),
                        StreamOffset.create(RedisKeys.streamChannel(), ReadOffset.lastConsumed()));
        List<Object> result = new ArrayList<>();
        if (records != null) {
            for (MapRecord<String, Object, Object> r : records) {
                result.add(r.getValue());
                stringRedisTemplate.opsForStream().acknowledge(RedisKeys.streamChannel(), GROUP, r.getId());
            }
        }
        return R.ok(result);
    }
}
