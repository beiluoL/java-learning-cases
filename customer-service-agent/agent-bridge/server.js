/**
 * CodeBuddy Agent 桥接服务（Node）
 * --------------------------------------------------
 * 复用 skill `codebuddy-chat-web` 中 server/index.ts 的 @tencent-ai/agent-sdk 集成模式，
 * 将 CodeBuddy 大模型能力以 HTTP 接口暴露给 Java 后端（Spring Boot）。
 *
 * 提供：
 *   GET  /health                 健康检查
 *   POST /api/agent/complete     接收 {userMessage, intent, intentLabel, faqs, history}
 *                                返回 {text: "..."}（非流式，由 Java 侧负责 SSE 分片推送）
 *
 * 鉴权：读取环境变量 CODEBUDDY_API_KEY（与 skill 一致）。
 */
import express from 'express';
import { query } from '@tencent-ai/agent-sdk';
import { fileURLToPath } from 'url';
import path from 'path';
import fs from 'fs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 加载 .env
try {
  const envPath = path.join(__dirname, '.env');
  if (fs.existsSync(envPath)) {
    for (const line of fs.readFileSync(envPath, 'utf-8').split('\n')) {
      const m = line.match(/^\s*([\w.-]+)\s*=\s*(.*)\s*$/);
      if (m && !process.env[m[1]]) process.env[m[1]] = m[2];
    }
  }
} catch (e) { /* ignore */ }

const app = express();
const PORT = process.env.PORT || 3001;
const MODEL = process.env.CODEBUDDY_MODEL || 'claude-sonnet-4';

app.use(express.json());

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', sdk: 'codebuddy-agent', hasKey: !!process.env.CODEBUDDY_API_KEY });
});

app.post('/api/agent/complete', async (req, res) => {
  const { userMessage, intentLabel, faqs = [], history = [] } = req.body || {};
  if (!userMessage) return res.status(400).json({ error: 'userMessage 不能为空' });

  const faqText = faqs.length
    ? faqs.map((f, i) => `${i + 1}. Q: ${f.question}\n   A: ${f.answer}`).join('\n')
    : '（无）';

  const systemPrompt =
    '你是一个电商平台的智能客服助手，请用中文、专业且亲切的语气回答用户问题。\n' +
    '请严格基于下方【知识库参考】作答；若参考内容不足以回答，请如实说明并建议用户回复「转人工」。\n' +
    '不要编造订单号、物流单号等具体数据。回答尽量分点、简洁。\n' +
    `【用户意图】${intentLabel || '未知'}\n` +
    '【知识库参考】\n' + faqText;

  const historyText = history.length
    ? history.map((m) => `${m.role === 'user' ? '用户' : '客服'}：${m.content}`).join('\n')
    : '';

  const prompt = (historyText ? historyText + '\n' : '') + '用户：' + userMessage;

  try {
    const stream = query({
      prompt,
      options: {
        model: MODEL,
        maxTurns: 1,
        systemPrompt,
        permissionMode: 'bypassPermissions'
      }
    });

    let full = '';
    for await (const msg of stream) {
      if (msg.type === 'assistant') {
        const c = msg.message?.content;
        if (typeof c === 'string') full += c;
        else if (Array.isArray(c)) {
          for (const b of c) if (b.type === 'text') full += b.text;
        }
      }
    }
    res.json({ text: full || '抱歉，我暂时无法回答，请回复「转人工」联系人工客服。' });
  } catch (e) {
    console.error('[Bridge] CodeBuddy 调用失败:', e?.message || e);
    res.status(502).json({ error: e?.message || 'CodeBuddy 调用失败' });
  }
});

app.listen(PORT, () => {
  console.log(`\n◉ CodeBuddy Agent 桥接服务已启动: http://localhost:${PORT}`);
  console.log(`  CODEBUDDY_API_KEY: ${process.env.CODEBUDDY_API_KEY ? '已配置' : '未配置（将返回兜底文案）'}\n`);
});
