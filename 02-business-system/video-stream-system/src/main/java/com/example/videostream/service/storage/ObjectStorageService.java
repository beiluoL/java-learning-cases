package com.example.videostream.service.storage;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 对象存储抽象（与具体云厂商解耦）。
 * 业务侧只依赖此接口，便于在 AWS S3 / 阿里云 OSS / 腾讯云 COS / 自建 MinIO 之间切换。
 */
public interface ObjectStorageService {

    /** 生成「上传」预签名 URL（客户端可直传，无需经过业务服务器） */
    String generatePresignedUploadUrl(String objectKey, Duration ttl);

    /** 发起分片上传，返回 uploadId（大文件断点续传的基础） */
    String initiateMultipartUpload(String objectKey);

    /** 生成某个分片的预签名上传 URL（multipart 第 partNumber 片） */
    String generatePresignedPartUrl(String objectKey, String uploadId, int partNumber, Duration ttl);

    /** 完成分片上传，合并为完整对象。partETags 为 分片号 -> ETag 映射 */
    void completeMultipartUpload(String objectKey, String uploadId, java.util.Map<Integer, String> partETags);

    /** 服务端下载对象到本地临时文件（Worker 转码前拉取源片） */
    void downloadFile(String objectKey, Path targetPath);

    /** 服务端上传本地文件到对象存储（Worker 转码后回传切片/播放列表） */
    void uploadFile(String objectKey, Path sourcePath);

    /** 判断对象是否存在 */
    boolean exists(String objectKey);

    /** 桶名 */
    String getBucket();

    /** 对象的基础访问路径前缀（用于拼接 CDN 域名） */
    String getObjectBasePath(String objectKey);
}
