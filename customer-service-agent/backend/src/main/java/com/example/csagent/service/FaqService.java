package com.example.csagent.service;

import com.example.csagent.dto.FaqMatch;
import com.example.csagent.dto.Intent;
import com.example.csagent.model.FaqItem;
import com.example.csagent.repository.FaqItemRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FAQ 知识库检索服务：基于「关键词重合度 + 标题命中加权 + 权重」的多路打分，
 * 返回与用户问题最相关的若干条目。可结合意图做分类检索以提升精度。
 */
@Service
public class FaqService {

    private final FaqItemRepository faqRepository;

    public FaqService(FaqItemRepository faqRepository) {
        this.faqRepository = faqRepository;
    }

    /** 检索 TopN 相关 FAQ（不限分类）。 */
    public List<FaqMatch> search(String query, int topN) {
        return search(query, null, topN);
    }

    /** 检索 TopN 相关 FAQ（可限定意图分类）。 */
    public List<FaqMatch> search(String query, Intent intent, int topN) {
        List<FaqItem> pool;
        if (intent != null && intent != Intent.UNKNOWN) {
            List<FaqItem> byCat = faqRepository.findByCategoryOrderByWeightDesc(intent.name());
            pool = byCat.isEmpty() ? faqRepository.findAllByOrderByCategoryAscWeightDesc() : byCat;
        } else {
            pool = faqRepository.findAllByOrderByCategoryAscWeightDesc();
        }

        String q = query.toLowerCase();
        List<String> qTokens = tokenize(q);

        List<FaqMatch> scored = new ArrayList<>();
        for (FaqItem item : pool) {
            double score = score(item, q, qTokens);
            if (score > 0) scored.add(FaqMatch.of(item, score));
        }
        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return scored.stream().limit(topN).collect(Collectors.toList());
    }

    private double score(FaqItem item, String q, List<String> qTokens) {
        double score = 0.0;
        // 标题命中权重最高
        if (item.getQuestion().toLowerCase().contains(q)) score += 5.0;
        // 关键词字段重合
        if (item.getKeywords() != null) {
            for (String kw : item.getKeywords().split("[,，]")) {
                kw = kw.trim().toLowerCase();
                if (!kw.isEmpty() && q.contains(kw)) score += 3.0;
            }
        }
        // 问题分词与答案分词重合
        List<String> aTokens = tokenize(item.getQuestion().toLowerCase());
        Set<String> aSet = new HashSet<>(tokenize(item.getAnswer().toLowerCase()));
        int overlap = 0;
        for (String t : qTokens) {
            // 单字噪声大，降权；2-gram 权重高
            double w = (t.length() == 1) ? 0.3 : 1.0;
            if (aSet.contains(t)) overlap += w;
            else if (aTokens.contains(t)) overlap += w;
        }
        score += overlap;
        // FAQ 自身权重微调
        score *= (1 + 0.05 * item.getWeight());
        return score;
    }

    /** 极简中文/英文分词：英文按词，中文按 2-gram + 单字。 */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        // 英文/数字词
        for (String w : text.split("[^a-z0-9]+")) {
            if (!w.isEmpty()) tokens.add(w);
        }
        // 中文 2-gram
        StringBuilder cjk = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) cjk.append(c);
            else if (cjk.length() > 0) { addNgrams(tokens, cjk.toString()); cjk.setLength(0); }
        }
        if (cjk.length() > 0) addNgrams(tokens, cjk.toString());
        return tokens;
    }

    private void addNgrams(List<String> tokens, String s) {
        for (int i = 0; i < s.length(); i++) tokens.add(String.valueOf(s.charAt(i)));
        for (int i = 0; i + 1 < s.length(); i++) tokens.add(s.substring(i, i + 2));
    }
}
