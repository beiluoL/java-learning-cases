package com.example.videostream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 转码档位（多清晰度）配置，对应 application.yml 中的 transcode.profiles。
 */
@Data
@Component
@ConfigurationProperties(prefix = "transcode")
public class TranscodeProperties {
    private List<TranscodeProfile> profiles = new ArrayList<>();

    @Data
    public static class TranscodeProfile {
        /** 档位名（作为切片目录与播放标识）：1080p / 720p / 480p / 360p */
        private String name;
        private int width;
        private int height;
        /** 视频编码：libx264（软）/ h264_nvenc（GPU） */
        private String videoCodec;
        private String videoBitrate;
        private String audioBitrate;
        /** 清晰度层级，1 最高；用于 ABR 升/降档排序 */
        private int tier;
    }
}
