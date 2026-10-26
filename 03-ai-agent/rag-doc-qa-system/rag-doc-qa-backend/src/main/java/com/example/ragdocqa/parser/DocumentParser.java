package com.example.ragdocqa.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Component
public class DocumentParser {

    private static final long MAX_CHARACTERS = 10 * 1024 * 1024; // 10MB 文本上限

    /**
     * 解析上传文件为纯文本
     */
    public String parse(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "unknown";
        }
        log.info("开始解析文档: {}", originalFilename);

        BodyContentHandler handler = new BodyContentHandler((int) MAX_CHARACTERS);
        Metadata metadata = new Metadata();
        metadata.set("resourceName", originalFilename);
        ParseContext context = new ParseContext();

        try (InputStream is = file.getInputStream()) {
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(is, handler, metadata, context);
        }

        String text = handler.toString();
        // 简单清洗：压缩多余空白
        text = text.replaceAll("\\r\\n|\\r", "\n").replaceAll("\\n\\s*\\n+", "\n\n").trim();
        log.info("文档解析完成: {}, 字符数: {}", originalFilename, text.length());
        return text;
    }
}
