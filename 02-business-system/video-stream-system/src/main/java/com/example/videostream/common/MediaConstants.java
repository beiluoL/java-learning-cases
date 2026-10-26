package com.example.videostream.common;

/**
 * 视频流系统全局常量。集中定义避免魔法值（阿里规范：不允许随意出现的数字/字符串字面量）。
 */
public final class MediaConstants {

    private MediaConstants() {
    }

    /** 对象存储路径前缀 */
    public static final String OSS_PREFIX_SOURCE = "source/";      // 原始上传片
    public static final String OSS_PREFIX_RAW = "raw/";            // 合并后的原始视频
    public static final String OSS_PREFIX_TRANSCODE = "transcode/"; // 转码产物（按 mediaId/档位 组织）

    /** 上传会话默认有效期（小时） */
    public static final long UPLOAD_SESSION_TTL_HOURS = 24;

    /** 分片大小下限（5MB，多数对象存储分片上传最小单位） */
    public static final long MIN_CHUNK_SIZE = 5L * 1024 * 1024;

    /** HLS 切片时长（秒） */
    public static final int HLS_SEGMENT_SECONDS = 6;

    /** 预签名 URL 默认有效期（分钟） */
    public static final int PRESIGN_TTL_MINUTES = 30;

    /** 播放鉴权 URL 默认有效期（秒） */
    public static final int PLAYBACK_URL_TTL_SECONDS = 3600;

    /** 媒资状态枚举值 */
    public enum MediaStatus {
        UPLOADING,       // 上传中
        TRANSCODING,     // 转码中
        READY,           // 可播放
        FAILED          // 失败
    }

    /** 转码任务状态枚举值 */
    public enum TaskStatus {
        PENDING,         // 待执行
        RUNNING,         // 执行中
        SUCCESS,         // 成功
        FAILED          // 失败
    }
}
