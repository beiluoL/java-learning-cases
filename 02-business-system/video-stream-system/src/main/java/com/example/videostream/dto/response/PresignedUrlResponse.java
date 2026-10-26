package com.example.videostream.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建上传会话的响应：返回会话ID、过期时间与各分片的预签名直传 URL。
 * 客户端据此跳过业务服务器，直接将每片 PUT 到对象存储。
 */
@Data
public class PresignedUrlResponse implements Serializable {

    private String sessionId;
    private LocalDateTime expireAt;
    /** 分片上传 uploadId（multipart 会话标识） */
    private String uploadId;
    /** 目标完整对象 key（所有分片最终合并为此对象） */
    private String objectKey;
    /** 每个分片序号对应的预签名上传 URL */
    private List<ChunkUploadUrl> chunkUrls;

    @Data
    public static class ChunkUploadUrl implements Serializable {
        /** 分片序号，从 0 开始 */
        private int index;
        /** 该分片直传对象存储的预签名 URL */
        private String url;
        /** 该分片在服务端对象存储的 key（便于完成后校验） */
        private String objectKey;
    }
}
