package com.example.ragdocqa.service;

import com.example.ragdocqa.config.RagProperties;
import com.example.ragdocqa.dto.UploadResult;
import com.example.ragdocqa.parser.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final DocumentParser documentParser;
    private final ChunkService chunkService;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RagProperties ragProperties;

    /**
     * 处理上传文档：解析 → 切片 → 向量化 → 存入 Milvus
     */
    public UploadResult processDocument(MultipartFile file) throws Exception {
        String docName = file.getOriginalFilename();
        if (docName == null || docName.isBlank()) {
            docName = "unknown";
        }

        String text = documentParser.parse(file);
        if (text.isBlank()) {
            throw new IllegalArgumentException("文档内容为空，无法处理");
        }

        List<String> chunks = chunkService.split(text, ragProperties.getChunkSize(), ragProperties.getChunkOverlap());
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档切片结果为空");
        }

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document doc = new Document(
                    chunks.get(i),
                    Map.of(
                            "doc_name", docName,
                            "chunk_index", String.valueOf(i)
                    )
            );
            documents.add(doc);
        }

        vectorStore.add(documents);
        log.info("文档向量化入库完成: {}, chunks: {}", docName, documents.size());

        return new UploadResult(docName, documents.size(), text.length());
    }

    /**
     * 基于检索上下文流式生成答案
     */
    public Flux<String> chat(String question) {
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(ragProperties.getTopK())
                        .build()
        );

        String context = relevantDocs.stream()
                .map(Document::getText)
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("");

        String systemPrompt = buildSystemPrompt(context);
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(question)
        ));

        return chatModel.stream(prompt)
                .map(response -> response.getResult().getOutput().getText());
    }

    private String buildSystemPrompt(String context) {
        return """
                你是一个严谨的文档智能问答助手。请严格根据以下「参考资料」回答用户问题。
                如果参考资料中没有足够信息，请明确告知用户无法从已上传文档中找到答案，不要编造。

                【参考资料】
                %s

                回答要求：
                1. 尽量使用参考资料中的原文表述；
                2. 如果涉及多个要点，请分点说明；
                3. 保持简洁、准确、完整。
                """.formatted(context);
    }
}
