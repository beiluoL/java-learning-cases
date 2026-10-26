package com.example.csagent.service;

import com.example.csagent.dto.AgentContext;
import com.example.csagent.dto.FaqMatch;
import com.example.csagent.dto.Intent;
import com.example.csagent.dto.IntentResult;
import com.example.csagent.model.FaqItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地规则引擎实现的 Agent 回复生成器（离线可用，无需任何外部 API）。
 * 基于已命中的 FAQ 条目生成「接地气」的回答，并附带相关问题建议。
 */
@Service
public class LocalAgentService implements LlmService {

    @Override
    public String generate(AgentContext ctx) {
        List<FaqMatch> faqs = ctx.getFaqs() == null ? List.of() : ctx.getFaqs();
        IntentResult intent = ctx.getIntent();

        if (faqs.isEmpty()) {
            return "抱歉，我暂时没能从知识库中找到完全匹配的答案。您可以换个说法描述问题，"
                    + "或输入「转人工」由人工客服为您处理。";
        }

        FaqMatch top = faqs.get(0);
        FaqItem topItem = top.getItem();

        StringBuilder sb = new StringBuilder();
        sb.append(intentPrefix(intent)).append("\n\n");
        sb.append(topItem.getAnswer());

        // 相关问题建议（去重，最多 2 条，排除已作为主答案的那条）
        List<String> suggestions = faqs.stream()
                .skip(1)
                .map(m -> m.getItem().getQuestion())
                .filter(q -> !q.equals(topItem.getQuestion()))
                .distinct()
                .limit(2)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!suggestions.isEmpty()) {
            sb.append("\n\n您可能还想了解：");
            for (int i = 0; i < suggestions.size(); i++) {
                sb.append("\n").append(i + 1).append(". ").append(suggestions.get(i));
            }
        }
        return sb.toString();
    }

    private String intentPrefix(IntentResult intent) {
        if (intent == null || intent.getIntent() == null) return "您好，为您查询到以下信息";
        return switch (intent.getIntent()) {
            case REFUND -> "关于退款问题";
            case ORDER_QUERY -> "关于订单查询";
            case TECH_SUPPORT -> "关于技术/使用问题";
            default -> "为您查询到以下信息";
        };
    }
}
