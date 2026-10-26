package com.example.videostream.service.media;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.domain.MediaAsset;
import com.example.videostream.repository.MediaAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 媒资查询服务（轻量封装）。
 */
@Slf4j
@Service
public class MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;

    public MediaAssetService(MediaAssetRepository mediaAssetRepository) {
        this.mediaAssetRepository = mediaAssetRepository;
    }

    public MediaAsset getRequired(String mediaId) {
        return mediaAssetRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "媒资不存在: " + mediaId));
    }
}
