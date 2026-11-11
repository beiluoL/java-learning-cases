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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【DEMO / 教学用】本地哈希嵌入模型，不是语义嵌入模型。
 *
 * <p>目的：让整个 Spring AI + Milvus 链路在零 API Key、零外部服务的前提下跑通，
 * 方便学习 VectorStore、集合、相似度检索等概念。它只能表达「字面 Token 重叠」，
 * <b>不具备语义理解能力，禁止用于生产计费、效果评估或任何真实检索质量结论</b>。
 * 生产请替换为 OpenAI / DeepSeek / Ollama 等真实 EmbeddingModel（见 README 第 5 节）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>ASCII 单词按词哈希；CJK 汉字按单字 + 相邻二元组（bigram）哈希，保证中文不会变成零向量；</li>
 *   <li>哈希桶累加模拟词频，再作 L2 归一化，使点积等价于余弦相似度；</li>
 *   <li>确定性：同样输入永远得到同样向量。</li>
 * </ul>
 *
 * <p>历史缺陷修复：旧实现用 {@code split("\\W+")} 分词，而 Java 的 {@code \w}
 * 默认只匹配 ASCII，中文会被整段当作分隔符丢弃，导致纯中文查询生成零向量、检索完全失效。
 */
@Component
public class LocalHashingEmbeddingModel implements EmbeddingModel {

    /** 匹配 ASCII 单词或单个 CJK 汉字，其余符号（标点、空白）一律忽略。 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9_]+|[\\u4e00-\\u9fff]");

    private final int dimension;

    public LocalHashingEmbeddingModel(@Value("${app.embedding.dimension:256}") int dimension) {
        this.dimension = dimension;
    }

    /** 把文本变成固定维度向量：分词 → 哈希桶累加 → L2 归一化。 */
    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), dimension);
            vector[index] += 1.0f;
        }
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
     * 教学用分词：ASCII 单词原样保留；CJK 汉字逐字切分，并把相邻两字组成 bigram，
     * 让「向量」「数据」「数据库」这类中文短语也能产生可比较的重叠特征。
     * 使用 Locale.ROOT 避免土耳其语等区域的大小写陷阱。
     */
    private List<String> tokenize(String text) {
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        List<String> units = new ArrayList<>();
        while (matcher.find()) {
            units.add(matcher.group());
        }
        List<String> tokens = new ArrayList<>(units.size() * 2);
        for (int i = 0; i < units.size(); i++) {
            String unit = units.get(i);
            tokens.add(unit);
            if (i + 1 < units.size() && isCjk(unit) && isCjk(units.get(i + 1))) {
                tokens.add(unit + units.get(i + 1));
            }
        }
        return tokens;
    }

    private boolean isCjk(String unit) {
        if (unit.length() != 1) {
            return false;
        }
        char c = unit.charAt(0);
        return c >= '\u4e00' && c <= '\u9fff';
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    /**
     * 批量嵌入入口（Model 接口）。List&lt;String&gt; / List&lt;Document&gt; 的重载由接口默认实现提供，
     * 不要自行重写，否则会产生泛型擦除冲突。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    /** Spring AI 会用 dimensions() 校验向量库维度，显式返回避免依赖默认实现再算一次嵌入。 */
    @Override
    public int dimensions() {
        return dimension;
    }

    public int getDimension() {
        return dimension;
    }
}
