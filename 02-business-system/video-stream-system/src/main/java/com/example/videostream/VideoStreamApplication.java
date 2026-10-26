package com.example.videostream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 全球视频流系统 - 控制面/业务后端启动类。
 * <p>
 * 职责：上传会话管理、媒资元数据、转码任务编排与调度、CDN 分发与鉴权、播放信息下发。
 * 重算力（FFmpeg 转码）由 MQ 消费者在独立 Worker 节点执行，本进程无状态可水平扩展。
 * </p>
 *
 * @author video-stream-team
 */
@SpringBootApplication
@EnableAsync
public class VideoStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoStreamApplication.class, args);
    }
}
