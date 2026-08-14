package com.example.csagent.service;

import com.example.csagent.config.AppProperties;
import com.example.csagent.dto.*;
import com.example.csagent.model.Conversation;
import com.example.csagent.model.EscalationTicket;
import com.example.csagent.model.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 客服 Agent 编排核心：
 * 多轮上下文 -> 意图识别 -> FAQ 检索 -> (LLM 生成 | 自动转人工) -> 持久化。
 *
 * 决策树：
 *   1) 用户主动/强制转人工        -> 转人工（USER_REQUEST）
 *   2) 寒暄/致谢且无命中          -> 友好兜底（不转人工）
 *   3) FAQ 命中分数 >= 阈值       -> LLM 生成接地气回答
 *   4) 无法解决（低分/未知意图）  -> 自动转人工（UNKNOWN_INTENT / FAQ_MISS / LOW_CONFIDENCE）
 */
@Service
public class AgentService {

    private final IntentService intentService;
    private final FaqService faqService;
    private final ConversationService conversationService;
    private final EscalationService escalationService;
    private final LocalAgentService localAgent;
    private final CodeBuddyBridgeLlmService codeBuddyAgent;
    private final AppProperties appProperties;

    private static final Set<String> GREETINGS = Set.of(
            "你好", "您好", "hi", "hello", "在吗", "在么", "哈喽", "嗨", "下午好", "上午好", "早上好");
    private static final Set<String> GRATITUDE = Set.of(
            "谢谢", "感谢", "多谢", "辛苦了", "太好了", "棒", "赞", "好的谢谢");

    public AgentService(IntentService intentService,
                        FaqService faqService,
                        ConversationService conversationService,
                        EscalationService escalationService,
                        LocalAgentService localAgent,
                        CodeBuddyBridgeLlmService codeBuddyAgent,
                        AppProperties appProperties) {
        this.intentService = intentService;
        this.faqService = faqService;
        this.conversationService = conversationService;
        this.escalationService = escalationService;
        this.localAgent = localAgent;
        this.codeBuddyAgent = codeBuddyAgent;
        this.appProperties = appProperties;
    }

    @Transactional
    public AgentReply handle(Conversation conv, String userMessage, boolean forceEscalate, Consumer<String> tokenSink) {
        // 1) 主动/强制转人工
        if (forceEscalate || intentService.isEscalationRequest(userMessage)) {
            return escalate(conv, userMessage, "USER_REQUEST", "用户主动要求转人工", tokenSink);
        }

        IntentResult ir = intentService.recognize(userMessage);
        List<Message> priorHistory = conversationService.history(conv.getId());

        // 保存用户消息（持久化多轮上下文）
        conversationService.saveUserMessage(conv, userMessage, ir.getIntent());

        // 2) 寒暄 / 致谢兜底（不转人工）
        if (ir.getIntent() == Intent.UNKNOWN && isSmallTalk(userMessage)) {
            String reply = buildSmallTalk(userMessage);
            streamOut(reply, tokenSink);
            conversationService.saveAssistantMessage(conv, reply, Intent.UNKNOWN, null, false);
            AgentReply r = baseReply(ir);
            r.setContent(reply);
            r.setGreeting(true);
            return r;
        }

        // 3) FAQ 检索
        List<FaqMatch> faqs = faqService.search(userMessage, ir.getIntent(), 3);
        double topScore = faqs.isEmpty() ? 0 : faqs.get(0).getScore();
        double threshold = appProperties.getCodebuddy().getFaqScoreThreshold();
        // 未知意图时要求更高的命中分才作答，否则倾向转人工（避免答非所问）
        if (ir.getIntent() == Intent.UNKNOWN) threshold += 2.0;

        if (topScore >= threshold) {
            // 命中知识库 -> LLM 生成（CodeBuddy 或本地），逐 token 流式输出
            AgentContext ctx = new AgentContext();
            ctx.setUserMessage(userMessage);
            ctx.setIntent(ir);
            ctx.setFaqs(faqs);
            ctx.setHistory(priorHistory);
            LlmService engine = appProperties.getCodebuddy().isEnabled() ? codeBuddyAgent : localAgent;
            StringBuilder answerBuf = new StringBuilder();
            engine.streamGenerate(ctx, tok -> {
                answerBuf.append(tok);
                if (tokenSink != null) tokenSink.accept(tok);
            });
            String answer = answerBuf.toString();

            // 记录主导意图到会话
            if (conv.getIntent() == null && ir.getIntent() != Intent.UNKNOWN) {
                conv.setIntent(ir.getIntent().name());
                conversationService.updateStatus(conv, conv.getStatus());
            }
            String faqIds = faqs.stream().map(f -> String.valueOf(f.getItem().getId()))
                    .reduce((a, b) -> a + "," + b).orElse(null);
            conversationService.saveAssistantMessage(conv, answer, ir.getIntent(), faqIds, false);

            AgentReply r = baseReply(ir);
            r.setContent(answer);
            r.setFaqs(faqs);
            return r;
        }

        // 4) 无法解决 -> 自动转人工
        String reason = ir.getIntent() == Intent.UNKNOWN
                ? "UNKNOWN_INTENT" : (faqs.isEmpty() ? "FAQ_MISS" : "LOW_CONFIDENCE");
        String summary = "用户诉求：" + userMessage
                + (ir.getIntent() != Intent.UNKNOWN ? "（疑似意图：" + ir.getIntent().getLabel() + "）" : "");
        return escalate(conv, userMessage, reason, summary, ir, tokenSink);
    }

    private AgentReply escalate(Conversation conv, String userMessage, String reason, String summary, Consumer<String> tokenSink) {
        return escalate(conv, userMessage, reason, summary, null, tokenSink);
    }

    private AgentReply escalate(Conversation conv, String userMessage, String reason, String summary, IntentResult ir, Consumer<String> tokenSink) {
        if (ir == null) ir = intentService.recognize(userMessage);
        EscalationTicket ticket = escalationService.create(conv, reason, ir.getIntent(), summary);
        conversationService.markEscalated(conv);

        String content;
        if ("USER_REQUEST".equals(reason)) {
            content = "好的，正在为您转接人工客服。已生成工单 #" + ticket.getId()
                    + "，请稍候，人工坐席将尽快接入（服务时间 9:00-21:00）。";
        } else {
            content = "非常抱歉，您的问题我暂时无法自动处理。已为您转接人工客服，工单号 #"
                    + ticket.getId() + "。我们的坐席会尽快跟进，您也可以补充更多细节以便更快解决。";
        }
        conversationService.saveAssistantMessage(conv, content, ir.getIntent(), null, true);
        streamOut(content, tokenSink);

        AgentReply r = baseReply(ir);
        r.setContent(content);
        r.setEscalated(true);
        r.setEscalateTicketId(ticket.getId());
        r.setEscalateReason(reason);
        return r;
    }

    private AgentReply baseReply(IntentResult ir) {
        AgentReply r = new AgentReply();
        r.setIntent(ir.getIntent());
        r.setIntentConfidence(ir.getConfidence());
        r.setMatchedKeywords(ir.getMatchedKeywords());
        r.setEscalated(false);
        r.setGreeting(false);
        return r;
    }

    /** 把一段已确定的文本按 3 字分片流式推送（用于寒暄/转人工等不走 LLM 的场景）。 */
    private void streamOut(String text, Consumer<String> tokenSink) {
        if (text == null || tokenSink == null) return;
        int i = 0;
        int n = text.length();
        while (i < n) {
            int end = Math.min(i + 3, n);
            tokenSink.accept(text.substring(i, end));
            i = end;
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean isSmallTalk(String text) {        String lower = text.toLowerCase().trim();
        if (lower.length() > 12) return false; // 较长句子通常不是寒暄
        for (String g : GREETINGS) if (lower.contains(g)) return true;
        for (String g : GRATITUDE) if (lower.contains(g)) return true;
        return false;
    }

    private String buildSmallTalk(String text) {
        String lower = text.toLowerCase();
        for (String g : GRATITUDE) if (lower.contains(g))
            return "不客气，很高兴为您服务！如还有任何问题，随时告诉我，或输入「转人工」联系人工客服。";
        return "您好，我是您的智能客服助手，可以帮您处理退款、查询订单、技术使用等问题。"
                + "请描述您遇到的问题，或输入「转人工」转接人工客服。";
    }
}
