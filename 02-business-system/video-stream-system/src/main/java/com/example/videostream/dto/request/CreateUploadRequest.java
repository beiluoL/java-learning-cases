package com.example.videostream.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建上传会话请求。前端在开始前计算文件整体哈希（用于秒传/完整性校验），
 * 并声明分片参数，服务端据此签发各分片预签名 URL。
 */
@Data
public class CreateUploadRequest implements Serializable {

    @NotBlank(message = "filename 不能为空")
    private String filename;

    /** 文件整体 SHA-256（十六进制） */
    @NotBlank(message = "fileHash 不能为空")
    private String fileHash;

    @NotNull(message = "totalSize 不能为空")
    @Positive(message = "totalSize 必须为正数")
    private Long totalSize;

    @NotNull(message = "chunkSize 不能为空")
    @Positive(message = "chunkSize 必须为正数")
    private Long chunkSize;

    @NotNull(message = "totalChunks 不能为空")
    @Positive(message = "totalChunks 必须为正数")
    private Integer totalChunks;

    /** 标题（可选，缺省取 filename） */
    private String title;

    /** 上传者ID（可选） */
    private Long uploaderId;
}
