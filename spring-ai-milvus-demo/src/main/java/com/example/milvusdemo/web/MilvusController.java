package com.example.milvusdemo.web;

import com.example.milvusdemo.service.MilvusLearningService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST 接口：用浏览器或 curl 就能体验整套流程。
 *
 * 体验顺序：
 *   1) POST http://localhost:8080/api/milvus/seed        -> 写入示例文档
 *   2) GET  http://localhost:8080/api/milvus/search?q=向量数据库怎么用  -> 相似度检索
 *   3) GET  http://localhost:8080/api/milvus/embed?text=你好世界        -> 看文本变成向量
 */
@RestController
@RequestMapping("/api/milvus")
public class MilvusController {

    private final MilvusLearningService service;

    public MilvusController(MilvusLearningService service) {
        this.service = service;
    }

    @PostMapping("/seed")
    public String seed() {
        return service.seedSampleDocs();
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "3") int topK) {
        return service.search(q, topK);
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam String text) {
        return service.embedDemo(text);
    }
}
