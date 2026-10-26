package com.example.videostream.controller;

import com.example.videostream.common.Result;
import com.example.videostream.dto.request.CompleteUploadRequest;
import com.example.videostream.dto.request.CreateUploadRequest;
import com.example.videostream.dto.response.PresignedUrlResponse;
import com.example.videostream.service.upload.UploadService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上传接口：负责上传会话创建（签发分片直传 URL）与完成合并。
 * 实际音视频字节由客户端经预签名 URL 直传对象存储，本接口只处理元数据与控制流。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** 创建上传会话，返回各分片预签名直传 URL。 */
    @PostMapping("/sessions")
    public Result<PresignedUrlResponse> createSession(@Valid @RequestBody CreateUploadRequest request) {
        return Result.success(uploadService.createUploadSession(request));
    }

    /** 完成上传：校验分片 → 合并 → 触发转码（或秒传）。返回媒资ID。 */
    @PostMapping("/complete")
    public Result<String> complete(@Valid @RequestBody CompleteUploadRequest request) {
        return Result.success(uploadService.completeUpload(request));
    }
}
