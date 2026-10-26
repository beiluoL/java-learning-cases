package com.example.videostream.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 完成上传请求。客户端上报各分片上传后服务端返回的 ETag，服务端校验分片完整并合并。
 */
@Data
public class CompleteUploadRequest implements Serializable {

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    /** 各分片信息：分片号 + 上传返回的 ETag（用于服务端合并校验） */
    private List<PartInfo> parts;

    @Data
    public static class PartInfo implements Serializable {
        /** 分片号，从 1 开始（S3 multipart 规范） */
        private int partNumber;
        /** 该分片上传成功后服务端返回的 ETag */
        private String eTag;
    }
}
