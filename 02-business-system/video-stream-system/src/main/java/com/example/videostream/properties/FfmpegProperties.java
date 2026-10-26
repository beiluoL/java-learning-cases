package com.example.videostream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FFmpeg 运行参数（转码 Worker 节点生效）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ffmpeg")
public class FfmpegProperties {
    /** ffmpeg 可执行文件路径 */
    private String path = "/usr/bin/ffmpeg";
    /** 单 Worker 并发转码任务数 */
    private int maxConcurrent = 4;
    /** 是否启用 GPU 硬件编码（NVIDIA NVENC） */
    private boolean gpuEnabled = false;
    /** GPU 索引 */
    private int gpuIndex = 0;
}
