package com.example.videostream.service.media;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.common.MediaConstants;
import com.example.videostream.domain.MediaAsset;
import com.example.videostream.dto.response.PlaybackInfoResponse;
import com.example.videostream.properties.CdnProperties;
import com.example.videostream.properties.TranscodeProperties.TranscodeProfile;
import com.example.videostream.service.cdn.CdnDispatchService;
import com.example.videostream.service.transcode.TranscodeProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放信息服务：组装播放器需要的全部信息——主播放列表 + 各清晰度档位清单（均带就近 CDN 域名与防盗链签名）。
 * 媒资未就绪时返回状态，由客户端轮询直到 READY。
 */
@Slf4j
@Service
public class PlaybackService {

    private final MediaAssetService mediaAssetService;
    private final TranscodeProfileService profileService;
    private final CdnDispatchService cdnDispatchService;
    private final CdnProperties cdnProperties;

    public PlaybackService(MediaAssetService mediaAssetService,
                           TranscodeProfileService profileService,
                           CdnDispatchService cdnDispatchService,
                           CdnProperties cdnProperties) {
        this.mediaAssetService = mediaAssetService;
        this.profileService = profileService;
        this.cdnDispatchService = cdnDispatchService;
        this.cdnProperties = cdnProperties;
    }

    public PlaybackInfoResponse getPlaybackInfo(String mediaId, String region) {
        MediaAsset asset = mediaAssetService.getRequired(mediaId);
        PlaybackInfoResponse resp = new PlaybackInfoResponse();
        resp.setMediaId(asset.getMediaId());
        resp.setTitle(asset.getTitle());
        resp.setStatus(asset.getStatus().name());
        resp.setDurationSec(asset.getDurationSec());

        if (asset.getStatus() != MediaConstants.MediaStatus.READY) {
            // 未就绪：仅返回状态，供客户端轮询
            return resp;
        }

        long ttl = cdnProperties.getDefaultTtlSeconds();
        resp.setMasterPlaylistUrl(cdnDispatchService.buildSignedUrl(asset.getMasterPlaylistKey(), region, ttl));
        if (asset.getCoverKey() != null) {
            resp.setCoverUrl(cdnDispatchService.buildSignedUrl(asset.getCoverKey(), region, ttl));
        }

        List<PlaybackInfoResponse.AbrProfile> profiles = new ArrayList<>();
        for (TranscodeProfile p : profileService.allOrdered()) {
            String playlistKey = MediaConstants.OSS_PREFIX_TRANSCODE + mediaId + "/" + p.getName() + ".m3u8";
            PlaybackInfoResponse.AbrProfile ap = new PlaybackInfoResponse.AbrProfile();
            ap.setName(p.getName());
            ap.setWidth(p.getWidth());
            ap.setHeight(p.getHeight());
            ap.setBandwidth(parseKbps(p.getVideoBitrate()) * 1000 + parseKbps(p.getAudioBitrate()) * 1000);
            ap.setPlaylistUrl(cdnDispatchService.buildSignedUrl(playlistKey, region, ttl));
            profiles.add(ap);
        }
        resp.setAbrProfiles(profiles);
        return resp;
    }

    private int parseKbps(String v) {
        try {
            return Integer.parseInt(v.replace("k", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
