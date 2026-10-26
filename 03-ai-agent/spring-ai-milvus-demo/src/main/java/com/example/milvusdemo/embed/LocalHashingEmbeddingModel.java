package com.example.milvusdemo.embed;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个“教学用”的本地嵌入模型（EmbeddingModel 实现）。
 *
 * 真实项目里，嵌入通常由 OpenAI / 本地 Ollama 等大模型完成（需要 API Key）。
 * 为了让你【零依赖、不填任何 Key】就能把整个 Spring AI + Milvus 流程跑起来，
 * 这里用“分词哈希 + L2 归一化”手写了一个 256 维向量。
 *
 * 它的特点：
 *  - 同样的词总是落到同一个维度（确定性）；
 *  - 词汇重叠越多的两段文本，向量越接近（余弦相似度越高）。
 * 因此足以演示“语义相似搜索”的核心思想，方便你专注于学习 Milvus + VectorStore 的玩法。
 *
 * 想换成真实模型时，把这个 Bean 换成 OpenAiEmbeddingModel 即可（见 README 末尾）。
 */
@Component
public class LocalHashingEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    public LocalHashingEmbeddingModel(@Value("${app.embedding.dimension:256}") int dimension) {
        this.dimension = dimension;
    }

    /**
     * 把一个文本变成固定维度的向量：对分词做哈希桶累加并做 L2 归一化。
     * 这是 EmbeddingModel 的核心抽象方法（单条文本）。
     */
    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }
        // 按非单词字符切词，转小写，去除标点影响
        String[] tokens = text.toLowerCase().split("\\W+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            // 把单词哈希到一个固定维度下标，并累加权重（词频思想）
            int index = Math.floorMod(token.hashCode(), dimension);
            vector[index] += 1.0f;
        }
        // L2 归一化，使向量长度恒为 1，方便用“点积=余弦相似度”比较
        double norm = 0.0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    /**
     * 单条文档的嵌入：直接复用文本的嵌入逻辑（EmbeddingModel 的抽象方法之一）。
     */
    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    /**
     * 批量嵌入入口（实现 Model 接口）：对每条文本调用 embed(String)。
     * 注意：List<String> / List<Document> 的重载由 EmbeddingModel 接口默认实现提供，
     * 不要自己重写（会有泛型擦除冲突）。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            // Embedding 构造器必须带 index：new Embedding(float[] vector, Integer index)
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    public int getDimension() {
        return dimension;
    }
}
