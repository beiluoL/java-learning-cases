package com.example.videostream.service.upload;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.common.MediaConstants;
import com.example.videostream.domain.MediaAsset;
import com.example.videostream.domain.UploadSession;
import com.example.videostream.dto.request.CompleteUploadRequest;
import com.example.videostream.dto.request.CreateUploadRequest;
import com.example.videostream.dto.response.PresignedUrlResponse;
import com.example.videostream.repository.MediaAssetRepository;
import com.example.videostream.repository.UploadSessionRepository;
import com.example.videostream.service.storage.ObjectStorageService;
import com.example.videostream.service.transcode.TranscodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 上传服务（控制面）：
 *  - 创建上传会话并签发「分片直传」预签名 URL（客户端绕过业务服务器直传对象存储）；
 *  - 完成时合并分片（multipart complete）；
 *  - 秒传去重：相同 fileHash 且已有 READY 媒资时直接复用，省去转码与存储；
 *  - 成功后创建媒资并触发转码编排。
 */
@Slf4j
@Service
public class UploadService {

    private final ObjectStorageService storage;
    private final UploadSessionRepository sessionRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final TranscodeService transcodeService;

    public UploadService(ObjectStorageService storage,
                         UploadSessionRepository sessionRepository,
                         MediaAssetRepository mediaAssetRepository,
                         TranscodeService transcodeService) {
        this.storage = storage;
        this.sessionRepository = sessionRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.transcodeService = transcodeService;
    }

    /** 创建上传会话，返回各分片预签名 URL。S3 multipart 分片号从 1 开始。 */
    @Transactional
    public PresignedUrlResponse createUploadSession(CreateUploadRequest req) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String ext = extractExt(req.getFilename());
        String objectKey = MediaConstants.OSS_PREFIX_RAW + sessionId + ext;
        String uploadId = storage.initiateMultipartUpload(objectKey);

        UploadSession session = new UploadSession();
        session.setSessionId(sessionId);
        session.setFileHash(req.getFileHash());
        session.setFilename(req.getFilename());
        session.setTotalSize(req.getTotalSize());
        session.setChunkSize(req.getChunkSize());
        session.setTotalChunks(req.getTotalChunks());
        session.setUploaderId(req.getUploaderId());
        session.setUploadId(uploadId);
        session.setObjectKey(objectKey);
        session.setStatus(UploadSession.SessionStatus.INIT);
        session.setExpireAt(LocalDateTime.now().plusHours(MediaConstants.UPLOAD_SESSION_TTL_HOURS));
        sessionRepository.save(session);

        Duration ttl = Duration.ofMinutes(MediaConstants.PRESIGN_TTL_MINUTES);
        List<PresignedUrlResponse.ChunkUploadUrl> chunkUrls = new ArrayList<>();
        for (int partNumber = 1; partNumber <= req.getTotalChunks(); partNumber++) {
            String url = storage.generatePresignedPartUrl(objectKey, uploadId, partNumber, ttl);
            PresignedUrlResponse.ChunkUploadUrl cu = new PresignedUrlResponse.ChunkUploadUrl();
            cu.setIndex(partNumber);
            cu.setUrl(url);
            cu.setObjectKey(objectKey);
            chunkUrls.add(cu);
        }

        PresignedUrlResponse resp = new PresignedUrlResponse();
        resp.setSessionId(sessionId);
        resp.setUploadId(uploadId);
        resp.setObjectKey(objectKey);
        resp.setExpireAt(session.getExpireAt());
        resp.setChunkUrls(chunkUrls);
        return resp;
    }

    /** 完成上传：校验分片完整性 → 合并 → 秒传判断 → 触发转码。返回媒资ID。 */
    @Transactional
    public String completeUpload(CompleteUploadRequest req) {
        UploadSession session = sessionRepository.findBySessionId(req.getSessionId())
                .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));
        if (session.getStatus() == UploadSession.SessionStatus.EXPIRED
                || session.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }
        if (req.getParts() == null || req.getParts().size() != session.getTotalChunks()) {
            throw new BizException(ErrorCode.UPLOAD_CHUNK_MISSING,
                    "分片数不符：期望 " + session.getTotalChunks() + "，实际 " + (req.getParts() == null ? 0 : req.getParts().size()));
        }

        // 组装 partNumber -> ETag
        Map<Integer, String> partETags = new LinkedHashMap<>();
        for (CompleteUploadRequest.PartInfo p : req.getParts()) {
            partETags.put(p.getPartNumber(), p.getETag());
        }
        storage.completeMultipartUpload(session.getObjectKey(), session.getUploadId(), partETags);

        // 秒传去重：相同哈希且已 READY 的媒资直接复用
        if (session.getFileHash() != null) {
            var existed = mediaAssetRepository.findByFileHash(session.getFileHash());
            if (existed.isPresent() && existed.get().getStatus() == MediaConstants.MediaStatus.READY) {
                MediaAsset reused = buildAsset(session, existed.get().getSourceObjectKey(),
                        existed.get().getMasterPlaylistKey());
                mediaAssetRepository.save(reused);
                session.setStatus(UploadSession.SessionStatus.MERGED);
                sessionRepository.save(session);
                log.info("秒传命中 mediaId={} hash={}", reused.getMediaId(), session.getFileHash());
                return reused.getMediaId();
            }
        }

        // 正常流程：新建媒资并触发转码
        MediaAsset asset = buildAsset(session, session.getObjectKey(), null);
        asset.setStatus(MediaConstants.MediaStatus.TRANSCODING);
        mediaAssetRepository.save(asset);
        session.setStatus(UploadSession.SessionStatus.MERGED);
        sessionRepository.save(session);

        transcodeService.scheduleTranscode(asset.getMediaId(), session.getObjectKey());
        log.info("upload completed, transcoding scheduled mediaId={}", asset.getMediaId());
        return asset.getMediaId();
    }

    private MediaAsset buildAsset(UploadSession session, String sourceKey, String masterKey) {
        MediaAsset asset = new MediaAsset();
        asset.setMediaId(UUID.randomUUID().toString().replace("-", ""));
        asset.setTitle(session.getFilename());
        asset.setOriginalFilename(session.getFilename());
        asset.setFileHash(session.getFileHash());
        asset.setSourceObjectKey(sourceKey);
        asset.setMasterPlaylistKey(masterKey);
        asset.setTotalSize(session.getTotalSize());
        asset.setUploaderId(session.getUploaderId());
        asset.setStatus(masterKey != null ? MediaConstants.MediaStatus.READY : MediaConstants.MediaStatus.UPLOADING);
        return asset;
    }

    private String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".mp4";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
