package com.example.videostream.service.storage;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.properties.ObjectStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 基于 AWS SDK v2（S3 兼容）的对象存储实现。
 * 适配：AWS S3、阿里云 OSS（S3 兼容接口）、腾讯云 COS（S3 兼容）、MinIO。
 */
@Slf4j
@Service
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ObjectStorageProperties props;

    public S3ObjectStorageService(S3Client s3Client, S3Presigner s3Presigner,
                                  ObjectStorageProperties props) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.props = props;
    }

    @Override
    public String generatePresignedUploadUrl(String objectKey, Duration ttl) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(objectRequest)
                    .build();
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("generatePresignedUploadUrl failed key={}", objectKey, e);
            throw new BizException(ErrorCode.STORAGE_PRESIGN_FAILED, e.getMessage());
        }
    }

    @Override
    public void downloadFile(String objectKey, Path targetPath) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build();
            s3Client.getObject(request, targetPath);
        } catch (NoSuchKeyException ex) {
            throw new BizException(ErrorCode.STORAGE_OBJECT_NOT_FOUND, objectKey);
        } catch (Exception e) {
            log.error("downloadFile failed key={}", objectKey, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "下载源文件失败");
        }
    }

    @Override
    public void uploadFile(String objectKey, Path sourcePath) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(sourcePath));
        } catch (Exception e) {
            log.error("uploadFile failed key={}", objectKey, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "上传转码产物失败");
        }
    }

    @Override
    public String initiateMultipartUpload(String objectKey) {
        try {
            CreateMultipartUploadResponse resp = s3Client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(props.getBucket())
                            .key(objectKey)
                            .build());
            return resp.uploadId();
        } catch (Exception e) {
            log.error("initiateMultipartUpload failed key={}", objectKey, e);
            throw new BizException(ErrorCode.STORAGE_PRESIGN_FAILED, "发起分片上传失败");
        }
    }

    @Override
    public String generatePresignedPartUrl(String objectKey, String uploadId, int partNumber, Duration ttl) {
        try {
            UploadPartRequest partRequest = UploadPartRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            UploadPartPresignRequest presign = UploadPartPresignRequest.builder()
                    .signatureDuration(ttl)
                    .uploadPartRequest(partRequest)
                    .build();
            return s3Presigner.presignUploadPart(presign).url().toString();
        } catch (Exception e) {
            log.error("generatePresignedPartUrl failed key={} part={}", objectKey, partNumber, e);
            throw new BizException(ErrorCode.STORAGE_PRESIGN_FAILED, "生成分片上传地址失败");
        }
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId,
                                        java.util.Map<Integer, String> partETags) {
        try {
            List<CompletedPart> parts = partETags.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(e -> CompletedPart.builder()
                            .partNumber(e.getKey())
                            .eTag(e.getValue())
                            .build())
                    .toList();
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
        } catch (Exception e) {
            log.error("completeMultipartUpload failed key={}", objectKey, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "合并分片失败");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            HeadObjectResponse resp = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(objectKey)
                    .build());
            return resp != null;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("headObject failed key={}", objectKey, e);
            return false;
        }
    }

    @Override
    public String getBucket() {
        return props.getBucket();
    }

    @Override
    public String getObjectBasePath(String objectKey) {
        // 拼接为可被 CDN 域名替换的相对路径：/bucket/key
        return "/" + props.getBucket() + "/" + objectKey;
    }
}
