package com.example.milvusdemo.service;

import com.example.milvusdemo.embed.LocalHashingEmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习服务：把“文档 -> 向量 -> 存入 Milvus -> 相似度检索”串成可调用的演示方法。
 *
 * 关键点：
 *  - VectorStore 是 Spring AI 的统一抽象，底层是 Milvus 还是 PGVector，业务代码都不用改；
 *  - add() 内部会调用 EmbeddingModel 把文本变成向量，再写入 Milvus；
 *  - similaritySearch() 会把查询文本也向量化，然后在 Milvus 里找最邻近的向量。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
public class MilvusLearningService {

    private final VectorStore vectorStore;
    private final LocalHashingEmbeddingModel embeddingModel;

    public MilvusLearningService(VectorStore vectorStore, LocalHashingEmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /** 往集合里写入几条示例文档（带元数据，类似给文章打标签） */
    public String seedSampleDocs() {
        List<Document> docs = List.of(
                new Document("Java 是一种面向对象的编程语言，支持封装、继承和多态。",
                        Map.of("category", "java-basic", "source", "handbook")),
                new Document("Spring Boot 通过自动配置和起步依赖，极大简化了 Spring 应用的搭建。",
                        Map.of("category", "spring-boot", "source", "handbook")),
                new Document("Milvus 是开源的向量数据库，擅长存储和检索高维向量，常用于 RAG 与语义搜索。",
                        Map.of("category", "milvus", "source", "handbook")),
                new Document("Spring AI 提供了统一的 VectorStore 抽象，可以无缝切换 Milvus、PGVector 等不同向量库。",
                        Map.of("category", "spring-ai", "source", "handbook")),
                new Document("向量检索的核心是计算余弦相似度，把文本转成向量后在向量空间里找最近的邻居。",
                        Map.of("category", "embedding", "source", "handbook"))
        );
        vectorStore.add(docs);
        return "已写入 " + docs.size() + " 条文档到集合 java_learning_docs";
    }

    /** 相似度检索：返回与 query 最相关的 topK 条文档 */
    public List<Map<String, Object>> search(String query, int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
        return results.stream().map(doc -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", doc.getText());
            m.put("metadata", doc.getMetadata());
            return m;
        }).collect(Collectors.toList());
    }

    /** 直观感受“文本 -> 向量”的过程：返回维度、前 10 个分量、向量模长 */
    public Map<String, Object> embedDemo(String text) {
        float[] vec = embeddingModel.embed(text);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dimension", vec.length);
        m.put("first10", Arrays.copyOfRange(vec, 0, Math.min(10, vec.length)));
        // float[] 没有 Arrays.stream，用 for 循环求 L2 模长
        double sumSquares = 0.0;
        for (float v : vec) {
            sumSquares += (double) v * v;
        }
        m.put("norm", Math.sqrt(sumSquares));
        return m;
    }
}
