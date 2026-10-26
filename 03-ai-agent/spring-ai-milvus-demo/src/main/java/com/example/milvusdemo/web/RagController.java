package com.example.milvusdemo.web;

import com.example.milvusdemo.service.DocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 摄取 / 检索的 REST 入口。
 *
 * 端点一览：
 *   POST /api/rag/ingest   上传文件 -> Tika 解析 -> 切块 -> 入库 Milvus
 *   POST /api/rag/detect   仅检测文件类型(MIME)，不入库
 *   GET  /api/rag/search   召回与 query 最相关的 chunk 列表
 *   GET  /api/rag/context  把召回结果拼成可喂给 LLM 的上下文
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final DocumentIngestionService ingestionService;

    public RagController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> ingest(@RequestParam("file") MultipartFile file) throws IOException {
        DocumentIngestionService.IngestionResult r = ingestionService.ingest(file);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filename", file.getOriginalFilename());
        m.put("chunkCount", r.chunkCount());
        m.put("detectedType", r.detectedType());
        m.put("metadata", r.metadata());
        return m;
    }

    @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> detect(@RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filename", file.getOriginalFilename());
        m.put("detectedType", ingestionService.detectType(file));
        return m;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q,
                                            @RequestParam(defaultValue = "3") int topK) {
        return ingestionService.retrieve(q, topK);
    }

    @GetMapping("/context")
    public Map<String, Object> context(@RequestParam String q,
                                       @RequestParam(defaultValue = "3") int topK) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("query", q);
        m.put("context", ingestionService.buildContext(q, topK));
        return m;
    }
}
