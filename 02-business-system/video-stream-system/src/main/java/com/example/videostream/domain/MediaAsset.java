package com.example.videostream.domain;

import com.example.videostream.common.MediaConstants.MediaStatus;
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
 * 媒资实体：一条视频从上传到转码就绪后的元数据聚合根。
 * 与转码任务（TranscodeTask）为一对多关系（每个清晰度档位一个任务）。
 */
@Getter
@Setter
@Entity
@Table(name = "media_asset", indexes = {
        @Index(name = "idx_file_hash", columnList = "file_hash"),
        @Index(name = "idx_status", columnList = "status")
})
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务媒资ID（对外暴露，避免自增ID被遍历） */
    @Column(name = "media_id", nullable = false, unique = true, length = 32)
    private String mediaId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    /** 文件整体哈希（秒传去重 / 完整性校验） */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /** 合并后原始视频在对象存储的 key */
    @Column(name = "source_object_key", length = 512)
    private String sourceObjectKey;

    /** 主播放列表（master m3u8）在对象存储的 key */
    @Column(name = "master_playlist_key", length = 512)
    private String masterPlaylistKey;

    /** 封面 key */
    @Column(name = "cover_key", length = 512)
    private String coverKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MediaStatus status = MediaStatus.UPLOADING;

    /** 时长（秒） */
    @Column(name = "duration_sec")
    private Integer durationSec;

    /** 总字节数 */
    @Column(name = "total_size")
    private Long totalSize;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
