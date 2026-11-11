package com.example.milvusdemo.embed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中文嵌入回归测试：锁死「纯中文文本不得生成零向量」这一历史缺陷。
 * 注意这里验证的是 DEMO 哈希嵌入的字面重叠能力，不是语义检索质量。
 */
class LocalHashingEmbeddingModelTest {

    private final LocalHashingEmbeddingModel model = new LocalHashingEmbeddingModel(256);

    private static double norm(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return Math.sqrt(sum);
    }

    private static double cosine(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    @Test
    void pureChineseTextMustNotProduceZeroVector() {
        float[] v = model.embed("向量数据库怎么用");
        assertEquals(256, v.length);
        assertEquals(1.0, norm(v), 1e-5, "纯中文文本必须被 L2 归一化");
    }

    @Test
    void chineseQueryShouldRankRelevantDocumentFirst() {
        String query = "向量数据库怎么用";
        String relevant = "Milvus 是开源的向量数据库，擅长存储和检索高维向量，常用于 RAG 与语义搜索。";
        String irrelevant = "Java 是一种面向对象的编程语言，支持封装、继承和多态。";
        float[] q = model.embed(query);
        assertTrue(cosine(q, model.embed(relevant)) > cosine(q, model.embed(irrelevant)),
                "中文查询应优先召回字面相关的文档");
    }

    @Test
    void deterministicAndEmptyTextHandled() {
        assertTrue(java.util.Arrays.equals(model.embed("缓存穿透"), model.embed("缓存穿透")));
        assertEquals(0.0, norm(model.embed("   ")), 0.0);
        assertEquals(256, model.dimensions());
    }
}
