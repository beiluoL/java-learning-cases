package com.example.videostream.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 播放信息响应：播放器据此拉取主播放列表与各档位清单，结合 ABR 自适应切换清晰度。
 * 所有 URL 均带时效性签名（防盗链），由 {@code PlaybackService} 生成。
 */
@Data
public class PlaybackInfoResponse implements Serializable {

    private String mediaId;
    private String title;
    /** 媒资状态（非 READY 时不可播放） */
    private String status;
    private Integer durationSec;
    /** 主播放列表（master m3u8）签名 URL */
    private String masterPlaylistUrl;
    /** 封面签名 URL */
    private String coverUrl;
    /** 可供 ABR 选择的清晰度档位（已按清晰度从高到低排序） */
    private List<AbrProfile> abrProfiles;

    @Data
    public static class AbrProfile implements Serializable {
        private String name;
        private int width;
        private int height;
        /** 视频码率（kbps 近似） */
        private int bandwidth;
        /** 该档位播放列表（带签名）URL */
        private String playlistUrl;
    }
}
