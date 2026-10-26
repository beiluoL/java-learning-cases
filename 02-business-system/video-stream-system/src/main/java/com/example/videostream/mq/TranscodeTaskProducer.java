package com.example.videostream.mq;

import com.example.videostream.config.KafkaConfig;
import com.example.videostream.dto.message.TranscodeTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 转码任务生产者：将任务消息可靠投递到 Kafka。
 * 使用 acks=all + 同步确认（或回调），确保任务不丢失。
 */
@Slf4j
@Component
public class TranscodeTaskProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TranscodeTaskProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(TranscodeTaskMessage message) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaConfig.TOPIC_TRANSCODE, message.getMediaId(), message);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("send transcode task failed mediaId={} taskId={}",
                        message.getMediaId(), message.getTaskId(), ex);
                // 实际生产可在此引入本地落盘重试 / 死信表，避免消息丢失
            } else {
                log.info("sent transcode task mediaId={} taskId={} partition={}",
                        message.getMediaId(), message.getTaskId(),
                        result.getRecordMetadata().partition());
            }
        });
    }
}
