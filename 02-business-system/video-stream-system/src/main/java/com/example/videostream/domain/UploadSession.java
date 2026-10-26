package com.example.videostream.domain;

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
 * 上传会话：分片直传场景下，前端先申请会话（拿到各分片预签名 URL），逐片上传，
 * 最后调用 complete 触发合并 / 秒传判断。
 * <p>
 * 注：uploadedChunks 以逗号分隔的分片序号存储（设计简化；生产可归一化为 upload_chunk 子表以支持海量分片查询）。
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "upload_session", indexes = {
        @Index(name = "idx_session_id", columnList = "session_id"),
        @Index(name = "idx_file_hash", columnList = "file_hash")
})
public class UploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 32)
    private String sessionId;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "filename", length = 512)
    private String filename;

    /** multipart 上传会话 uploadId */
    @Column(name = "upload_id", length = 256)
    private String uploadId;

    /** 合并后的目标对象 key（原始视频） */
    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "total_size")
    private Long totalSize;

    /** 单分片字节数 */
    @Column(name = "chunk_size")
    private Long chunkSize;

    /** 分片总数 */
    @Column(name = "total_chunks")
    private int totalChunks;

    /** 已上传分片序号（逗号分隔），如 "0,1,2" */
    @Column(name = "uploaded_chunks", length = 4096)
    private String uploadedChunks = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status = SessionStatus.INIT;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SessionStatus {
        INIT,      // 已创建，待上传
        PARTIAL,   // 部分分片已上传
        COMPLETED, // 全部分片上传完成
        MERGED,    // 已合并/秒传命中，可转码
        EXPIRED    // 过期
    }
}
