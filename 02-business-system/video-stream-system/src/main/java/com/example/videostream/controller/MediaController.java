package com.example.videostream.controller;

import com.example.videostream.common.Result;
import com.example.videostream.dto.response.PlaybackInfoResponse;
import com.example.videostream.service.media.PlaybackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒资/播放接口：下发播放信息（主播放列表 + 多清晰度档位清单，均带就近 CDN 与防盗链签名）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final PlaybackService playbackService;

    public MediaController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    /**
     * 获取播放信息。
     *
     * @param mediaId 媒资ID
     * @param region  观众区域（AP/EU/US/...），用于就近 CDN 域名选择；缺省用 DEFAULT
     */
    @GetMapping("/{mediaId}/playback")
    public Result<PlaybackInfoResponse> playback(@PathVariable String mediaId,
                                                 @RequestParam(required = false) String region) {
        return Result.success(playbackService.getPlaybackInfo(mediaId, region));
    }
}
