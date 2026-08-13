package com.example.ragdocqa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChunkService {

    /**
     * 按固定长度 + 重叠窗口切片
     *
     * @param text   文档全文
     * @param size   单块最大字符数
     * @param overlap 相邻块重叠字符数
     */
    public List<String> split(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        if (size <= 0) {
            size = 800;
        }
        if (overlap < 0 || overlap >= size) {
            overlap = size / 5;
        }

        int step = size - overlap;
        int start = 0;
        int len = text.length();

        while (start < len) {
            int end = Math.min(start + size, len);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == len) {
                break;
            }
            start += step;
        }

        log.info("文本切片完成: 总字符 {}, 块数 {}", len, chunks.size());
        return chunks;
    }
}
