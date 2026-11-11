package com.example.ai.optimize;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prompt 优化：只做无损变换，必须真的减少 Token 才记录节省。 */
class PromptOptimizerTest {

    private final PromptOptimizer optimizer = new PromptOptimizer(new TokenEstimator());

    @Test
    void deduplicatesIdenticalInstructionLines() {
        String prompt = "请使用中文回答。\n不要编造事实。\n请使用中文回答。\n不要编造事实。";
        PromptOptimizer.Result r = optimizer.optimize(prompt);
        assertEquals(2, r.after().split("\n").length);
        assertTrue(r.savedTokens() > 0);
        assertTrue(r.appliedRules().stream().anyMatch(x -> x.startsWith("LINE_DEDUP")));
    }

    @Test
    void collapsesExcessiveBlankLines() {
        String prompt = "第一段\n\n\n\n第二段";
        PromptOptimizer.Result r = optimizer.optimize(prompt);
        assertFalse(r.after().contains("\n\n\n"));
        assertTrue(r.beforeTokens() >= r.afterTokens());
    }

    @Test
    void keepsSemanticContentIntact() {
        String prompt = "必须遵守：不得泄露用户隐私。";
        PromptOptimizer.Result r = optimizer.optimize(prompt);
        assertEquals(prompt, r.after());
        assertEquals(0, r.savedTokens());
        assertFalse(r.changed());
    }

    @Test
    void emptyPromptIsSafe() {
        PromptOptimizer.Result r = optimizer.optimize("");
        assertEquals(0, r.beforeTokens());
        assertEquals(0, r.savedTokens());
    }

    @Test
    void stablePrefixIsDeterministicForProviderCaching() {
        String a = optimizer.composeSystemPrompt("场景：RAG 问答");
        String b = optimizer.composeSystemPrompt("场景：RAG 问答");
        assertEquals(a, b);
        assertTrue(a.startsWith(PromptOptimizer.STABLE_PREFIX));
    }

    @Test
    void estimatorIsHeuristicNotBilling() {
        TokenEstimator est = new TokenEstimator();
        // 中文按字计，英文按词 ×1.3，只是相对比较口径
        assertTrue(est.estimate("你好世界") >= 4);
        assertTrue(est.estimate("hello world") >= 2);
        assertEquals(0, est.estimate((String) null));
    }

    @Test
    void contextCompressorKeepsRecentRoundsAndDropsOld() {
        ContextCompressor compressor = new ContextCompressor(new TokenEstimator());
        List<ContextCompressor.Turn> turns = List.of(
                new ContextCompressor.Turn("user", "第一轮问题：什么是向量数据库"),
                new ContextCompressor.Turn("assistant", "第一轮回答：用于存储高维向量的数据库"),
                new ContextCompressor.Turn("user", "第二轮问题：Milvus 索引类型"),
                new ContextCompressor.Turn("assistant", "第二轮回答：IVF_FLAT / HNSW"),
                new ContextCompressor.Turn("user", "第三轮问题：怎么调 TopK"),
                new ContextCompressor.Turn("assistant", "第三轮回答：按召回评测结果调整"));
        ContextCompressor.Result r = compressor.compress(turns, 1, 10_000);
        // 保留最近 1 轮 = 2 条消息，其余 4 条被丢弃
        assertEquals(4, r.droppedTurns());
        assertEquals(3, r.turns().size());
        assertTrue(r.turns().get(0).text().contains("上下文已压缩"));
        assertTrue(r.savedTokens() > 0);
    }

    @Test
    void contextCompressorDedupesRepeatedMessages() {
        ContextCompressor compressor = new ContextCompressor(new TokenEstimator());
        List<ContextCompressor.Turn> turns = List.of(
                new ContextCompressor.Turn("user", "重复的问题"),
                new ContextCompressor.Turn("user", "重复的问题"));
        ContextCompressor.Result r = compressor.compress(turns, 4, 10_000);
        assertEquals(1, r.dedupedTurns());
    }

    @Test
    void ragOptimizerDedupesAndRespectsTokenBudget() {
        RagContextOptimizer optimizer = new RagContextOptimizer(new TokenEstimator());
        String fragment = "Milvus 是一个开源的向量数据库，支持多种索引类型与标量过滤能力，常用于语义检索场景。";
        String context = fragment + "\n\n" + fragment + "\n\n短片段";
        RagContextOptimizer.Result r = optimizer.optimize(context, 1000);
        assertEquals(1, r.fragmentCountAfter());
        assertTrue(r.savedTokens() > 0);

        RagContextOptimizer.Result limited = optimizer.optimize(context, 5);
        assertTrue(limited.afterTokens() <= 1000);
    }
}
