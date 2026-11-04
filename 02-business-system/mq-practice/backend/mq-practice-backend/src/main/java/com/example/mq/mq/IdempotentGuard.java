package com.example.mq.mq;

import com.example.mq.common.mq.MqScene;
import com.example.mq.config.MqProperties;
import com.example.mq.entity.MqIdempotentLog;
import com.example.mq.mapper.MqIdempotentLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消费幂等守卫 —— <b>双层幂等</b>。
 *
 * <h3>为什么需要两层？</h3>
 * <ul>
 *   <li><b>Redis SETNX（第一层）</b>：内存操作，微秒级，挡掉 99% 的重复投递，
 *       让绝大多数重复消息不碰数据库。</li>
 *   <li><b>DB 唯一键（第二层）</b>：Redis 可能被 flush、可能主从切换丢写、可能压根连不上，
 *       唯一索引是"永远正确"的最后防线。生产环境两层缺一不可。</li>
 * </ul>
 *
 * <h3>状态语义</h3>
 * <pre>
 *   处理成功 → mq_idempotent_log.status = 2(SUCCESS)  + Redis 键保留 24h
 *   处理失败 → status = 3(FAILED) + 删除 Redis 键 → 允许重试队列再次投递
 *   处理中   → status = 1(PROCESSING) → 重复消息直接丢弃（防止并发重复执行）
 * </pre>
 * 失败必须放行重试，否则「重试 3 次」的机制会被幂等自己挡住 —— 这是很多实现的隐藏 bug。
 */
@Slf4j
@Component
public class IdempotentGuard {

    private static final String KEY_PREFIX = "mq:idem:";

    private final StringRedisTemplate redisTemplate;
    private final MqIdempotentLogMapper idempotentLogMapper;
    private final MqProperties properties;
    private final ObjectMapper objectMapper;

    public IdempotentGuard(StringRedisTemplate redisTemplate,
                           MqIdempotentLogMapper idempotentLogMapper,
                           MqProperties properties,
                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.idempotentLogMapper = idempotentLogMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 尝试获取处理权。
     *
     * @return {@code true} = 本次可以处理；{@code false} = 已成功处理过（或正在处理），应直接 ack
     */
    public boolean tryAcquire(MqScene scene, String bizKey, String messageId, Object payload) {
        String key = dbKey(scene, bizKey);

        if (properties.isIdempotentEnabled()) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey(scene, bizKey), messageId == null ? "1" : messageId,
                            Duration.ofHours(properties.getIdempotentTtlHours()));

            if (Boolean.FALSE.equals(acquired)) {
                // Redis 说"处理过"了，但要区分：上次是成功，还是失败留下的锁？
                MqIdempotentLog existing = idempotentLogMapper.selectByBizKey(key);
                if (existing != null && existing.getStatus() != null
                        && existing.getStatus() == MqIdempotentLog.STATUS_SUCCESS) {
                    log.warn("[IDEMPOTENT] 命中已成功记录，跳过：scene={} bizKey={} messageId={}",
                            scene, bizKey, messageId);
                    return false;
                }
                if (existing != null && existing.getStatus() != null
                        && existing.getStatus() == MqIdempotentLog.STATUS_PROCESSING) {
                    log.warn("[IDEMPOTENT] 消息正在被其它消费者处理，跳过：scene={} bizKey={}", scene, bizKey);
                    return false;
                }
                // 上次是 FAILED，或者 Redis 有键但库里没有（进程崩溃残留）→ 放行重试
                redisTemplate.delete(redisKey(scene, bizKey));
                log.warn("[IDEMPOTENT] 上次处理失败/残留锁，放行重试：scene={} bizKey={}", scene, bizKey);
            }
        }

        try {
            MqIdempotentLog logEntity = new MqIdempotentLog();
            logEntity.setBizKey(key);
            logEntity.setScene(scene.name());
            logEntity.setMessageId(messageId);
            logEntity.setStatus(MqIdempotentLog.STATUS_PROCESSING);
            logEntity.setPayload(toJson(payload));
            idempotentLogMapper.insert(logEntity);
            return true;
        } catch (DuplicateKeyException e) {
            // DB 唯一键兜底：Redis 失效/多实例并发时，只有一条能插入成功
            MqIdempotentLog existing = idempotentLogMapper.selectByBizKey(key);
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() == MqIdempotentLog.STATUS_SUCCESS) {
                log.warn("[IDEMPOTENT] DB 唯一键拦截重复消息：scene={} bizKey={}", scene, bizKey);
                return false;
            }
            // 失败记录：更新回处理中，允许继续
            idempotentLogMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                    .<MqIdempotentLog>lambdaUpdate()
                    .eq(MqIdempotentLog::getBizKey, key)
                    .set(MqIdempotentLog::getStatus, MqIdempotentLog.STATUS_PROCESSING)
                    .set(MqIdempotentLog::getMessageId, messageId));
            return true;
        }
    }

    /** 业务处理成功：置为 SUCCESS，Redis 键继续保留到自然过期 */
    public void markSuccess(MqScene scene, String bizKey) {
        updateStatus(scene, bizKey, MqIdempotentLog.STATUS_SUCCESS);
    }

    /** 业务处理失败：置为 FAILED 并删除 Redis 键，让重试队列的下一轮投递可以进来 */
    public void markFailure(MqScene scene, String bizKey) {
        updateStatus(scene, bizKey, MqIdempotentLog.STATUS_FAILED);
        try {
            redisTemplate.delete(redisKey(scene, bizKey));
        } catch (Exception e) {
            log.warn("[IDEMPOTENT] 删除 Redis 幂等键失败（不影响重试，DB 会兜底）：{}", e.getMessage());
        }
    }

    private void updateStatus(MqScene scene, String bizKey, int status) {
        try {
            idempotentLogMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                    .<MqIdempotentLog>lambdaUpdate()
                    .eq(MqIdempotentLog::getBizKey, dbKey(scene, bizKey))
                    .set(MqIdempotentLog::getStatus, status));
        } catch (Exception e) {
            log.error("[IDEMPOTENT] 更新幂等状态失败 scene={} bizKey={} status={}", scene, bizKey, status, e);
        }
    }

    /** 数据库中的业务键：`SCENE:bizKey`，唯一索引建在它上面 */
    private String dbKey(MqScene scene, String bizKey) {
        return scene.name() + ':' + bizKey;
    }

    /** Redis 中的键：额外加命名空间前缀，避免与其它业务的 key 冲突 */
    private String redisKey(MqScene scene, String bizKey) {
        return KEY_PREFIX + dbKey(scene, bizKey);
    }

    private String toJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() > 2000 ? json.substring(0, 2000) : json;
        } catch (Exception e) {
            return null;
        }
    }
}
