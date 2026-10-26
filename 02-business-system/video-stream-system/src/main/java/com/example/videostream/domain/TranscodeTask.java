package com.example.videostream.domain;

import com.example.videostream.common.MediaConstants.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 转码任务：每个清晰度档位（1080p/720p/...）对应一条任务，由 MQ 分发到 Worker 执行。
 * 任务成功后在 {@link #outputPlaylistKey} 写入该档位的 m3u8 路径。
 */
@Getter
@Setter
@Entity
@Table(name = "transcode_task", indexes = {
        @Index(name = "idx_media_id", columnList = "media_id"),
        @Index(name = "idx_status", columnList = "status")
})
public class TranscodeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务ID（对外） */
    @Column(name = "task_id", nullable = false, unique = true, length = 32)
    private String taskId;

    /** 关联媒资ID */
    @Column(name = "media_id", nullable = false, length = 32)
    private String mediaId;

    /** 档位名：1080p / 720p / 480p / 360p */
    @Column(name = "profile_name", nullable = false, length = 16)
    private String profileName;

    /** 转码源（原始视频）对象存储 key */
    @Column(name = "source_object_key", nullable = false, length = 512)
    private String sourceObjectKey;

    /** 该档位切片输出前缀（对象存储目录） */
    @Column(name = "output_prefix", length = 512)
    private String outputPrefix;

    /** 该档位播放列表 key（成功后写入） */
    @Column(name = "output_playlist_key", length = 512)
    private String outputPlaylistKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaskStatus status = TaskStatus.PENDING;

    /** 重试次数 */
    @Column(name = "attempt")
    private int attempt = 0;

    /** 失败原因（便于排查） */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
