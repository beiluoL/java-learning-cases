package com.example.milvusdemo.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档摄取（Ingestion）服务：把“任意格式文件”变成“可被 RAG 检索的向量块”。
 *
 * 一条 RAG 摄取链路通常分 4 步：
 *   1) 类型检测  —— Tika 判断 MIME（如 text/plain、application/pdf）；
 *   2) 内容提取  —— Tika 把二进制文件解析成纯文本 + 元数据（标题/作者/页数…）；
 *   3) 文本分块  —— 长文切成若干 chunk，避免一次塞太多、也便于精准召回；
 *   4) 向量入库  —— VectorStore.add() 内部用 EmbeddingModel 把每个 chunk 文本向量化，写入 Milvus。
 *
 * 检索/召回环节另见 retrieve() / buildContext()。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
public class DocumentIngestionService {

    private final Tika tika = new Tika();
    private final VectorStore vectorStore;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 摄取一个上传的文件。返回切块数量、检测到的 MIME 类型、以及写入 Milvus 的元数据。
     */
    public IngestionResult ingest(MultipartFile file) throws IOException {
        String filename = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "unknown";

        // 1) 文件类型检测（MIME）。注意要单独开一个流，且检测后流已被消费，解析时需再开一次。
        String detectedType;
        try (InputStream probe = file.getInputStream()) {
            detectedType = tika.detect(probe);
        }

        // 2) 文本 + 元数据提取：共用同一个 Metadata 对象，parseToString 解析时会把元数据填进去。
        //    注意：parseToString 会抛受检异常 TikaException，这里转成 IOException 向上传递。
        Metadata metadata = new Metadata();
        // Tika 3.x 起该常量移动到 TikaCoreProperties，Metadata 类已不再提供
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        String fullText;
        try (InputStream in = file.getInputStream()) {
            fullText = tika.parseToString(in, metadata);
        } catch (TikaException e) {
            throw new IOException("Tika 解析文件失败: " + filename, e);
        }

        // 3) 组装元数据 Map：Tika 自带元数据 + 业务标签（来源文件名、内容类型、管道标记）
        Map<String, Object> baseMeta = new LinkedHashMap<>();
        baseMeta.put("source", filename);
        baseMeta.put("contentType", detectedType);
        baseMeta.put("pipeline", "tika-ingest");
        for (String name : metadata.names()) {
            String v = metadata.get(name);
            if (v != null && !v.isBlank()) {
                baseMeta.put("tika." + name, v);
            }
        }

        // 4) 文本分块（带重叠，避免切断语义）
        List<String> chunks = splitText(fullText, 400, 60);

        // 5) 批量写入 Milvus：VectorStore.add 内部会调用 EmbeddingModel 把每个 chunk 向量化
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new LinkedHashMap<>(baseMeta);
            meta.put("chunkIndex", i + 1);
            meta.put("chunkTotal", chunks.size());
            docs.add(new Document(chunks.get(i), meta));
        }
        vectorStore.add(docs);

        return new IngestionResult(docs.size(), detectedType, baseMeta);
    }

    /** 仅做类型检测，不入库（方便先确认 Tika 识别是否正确）。 */
    public String detectType(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return tika.detect(in);
        }
    }

    /** RAG 的“召回”环节：把 query 向量化后，在 Milvus 中找最相关的 chunk。 */
    public List<Map<String, Object>> retrieve(String query, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
        List<Map<String, Object>> out = new ArrayList<>(hits.size());
        for (Document d : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", d.getText());
            m.put("metadata", d.getMetadata());
            out.add(m);
        }
        return out;
    }

    /** RAG 的“拼装上下文”环节：把召回的 chunk 拼成可直接喂给 LLM 的上下文文本。 */
    public String buildContext(String query, int topK) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> hit : retrieve(query, topK)) {
            // Map<?, ?> 的 getOrDefault 第二参数会被 capture 成未知类型，改用 get + 判空
            Object src = ((Map<?, ?>) hit.get("metadata")).get("source");
            if (src == null) {
                src = "?";
            }
            sb.append("[来源: ").append(src).append("]\n").append(hit.get("content")).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 简单的“递归字符分块器”：先按空行分段，段内按 maxLen 切，段间保留 overlap 个字符重叠。
     * 学习用足够；生产环境可换成按句子/标题感知的切分器（如 langchain 的 RecursiveCharacterTextSplitter）。
     */
    private List<String> splitText(String text, int maxLen, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder buf = new StringBuilder();
        for (String para : paragraphs) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (buf.length() + p.length() + 1 > maxLen) {
                if (buf.length() > 0) {
                    chunks.add(buf.toString().trim());
                    int start = Math.max(0, buf.length() - overlap);
                    buf = new StringBuilder(buf.substring(start));
                }
                // 单段本身就超长：直接按 maxLen 强制切
                if (p.length() > maxLen) {
                    for (int i = 0; i < p.length(); i += maxLen) {
                        chunks.add(p.substring(i, Math.min(i + maxLen, p.length())));
                    }
                    buf = new StringBuilder();
                } else {
                    buf.append(p).append("\n");
                }
            } else {
                buf.append(p).append("\n");
            }
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString().trim());
        }
        return chunks;
    }

    /** 摄取结果（record，不可变）。 */
    public record IngestionResult(int chunkCount, String detectedType, Map<String, Object> metadata) {
    }
}
