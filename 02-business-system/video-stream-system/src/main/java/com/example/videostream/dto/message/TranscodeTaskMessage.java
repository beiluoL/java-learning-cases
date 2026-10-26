package com.example.videostream.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 转码任务消息：投递到 Kafka，由 Worker 消费执行。
 * 设计为可序列化 POJO（Spring Kafka JsonSerializer 依赖无参构造 + getter/setter）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodeTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;
    /** 媒资ID */
    private String mediaId;
    /** 档位名：1080p / 720p / 480p / 360p */
    private String profileName;
    /** 原始视频对象存储 key */
    private String sourceObjectKey;
    /** 该档位切片输出前缀（对象存储目录） */
    private String outputPrefix;
    /** 当前重试次数 */
    private int attempt;
}
