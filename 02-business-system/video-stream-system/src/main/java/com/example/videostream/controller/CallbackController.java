package com.example.videostream.controller;

import com.example.videostream.common.Result;
import com.example.videostream.dto.request.TranscodeCallbackRequest;
import com.example.videostream.service.transcode.TranscodeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回调接口：用于对接「云托管转码」（如 AWS MediaConvert / 阿里云 MTS）的结果通知。
 * 与 Kafka 消费者（自托管 Worker）是两种集成方式，二选一即可；均最终调用 TranscodeService.onTaskFinished 收敛状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/callbacks")
public class CallbackController {

    private final TranscodeService transcodeService;

    public CallbackController(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    /** 云转码完成回调：云厂商异步通知某档位转码结果。 */
    @PostMapping("/transcode")
    public Result<Void> transcodeCallback(@Valid @RequestBody TranscodeCallbackRequest request) {
        transcodeService.onTaskFinished(
                request.getTaskId(),
                request.getPlaylistKey(),
                request.isSuccess(),
                request.getErrorMessage());
        return Result.success();
    }
}
