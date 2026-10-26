package com.example.ragdocqa.controller;

import com.example.ragdocqa.dto.ApiResult;
import com.example.ragdocqa.dto.ChatRequest;
import com.example.ragdocqa.dto.UploadResult;
import com.example.ragdocqa.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;

    @PostMapping("/documents/upload")
    public ApiResult<UploadResult> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResult.error("上传文件为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return ApiResult.error("文件名异常");
        }
        String lower = originalName.toLowerCase();
        if (!lower.endsWith(".doc") && !lower.endsWith(".docx") && !lower.endsWith(".pdf")
                && !lower.endsWith(".txt") && !lower.endsWith(".md")) {
            return ApiResult.error("仅支持 DOC、DOCX、PDF、TXT、MD 格式");
        }

        try {
            UploadResult result = ragService.processDocument(file);
            return ApiResult.ok(result);
        } catch (Exception e) {
            log.error("文档处理失败", e);
            return ApiResult.error("文档处理失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@Valid @RequestBody ChatRequest request) {
        return ragService.chat(request.getQuestion())
                .delayElements(Duration.ofMillis(30))
                .onErrorResume(e -> {
                    log.error("问答失败", e);
                    return Flux.just("[ERROR] " + e.getMessage());
                });
    }
}
