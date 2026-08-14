/**
 * CodeBuddy Agent 桥接服务（Node）
 * --------------------------------------------------
 * 复用 skill `codebuddy-chat-web` 中 server/index.ts 的 @tencent-ai/agent-sdk 集成模式，
 * 将 CodeBuddy 大模型能力以 HTTP 接口暴露给 Java 后端（Spring Boot）。
 *
 * 提供：
 *   GET  /health                 健康检查（是否装好 CLI、是否配置 Key）
 *   POST /api/agent/stream       真实流式接口：逐 token 推送 SSE，事件 {type:'token'|'done'|'error'}
 *   POST /api/agent/complete     非流式兜底接口：返回 {text: "..."}（Java 侧 generate 用）
 *
 * 鉴权：底层由 @tencent-ai/codebuddy-code CLI 负责（读取本机 CodeBuddy CN 登录态 /
 *       环境变量 CODEBUDDY_API_KEY）。本服务只负责把 SDK 的流式输出转成 SSE 给 Java。
 *
 * 容错：CodeBuddy 不可达 / 被企业数据防护网关拦截 / 调用超时 时，本服务会发送 {type:'error'}
 *       事件并结束，由 Java 侧无缝降级到本地规则引擎（仍保持流式输出）。
 */
import express from 'express';
import { query } from '@tencent-ai/agent-sdk';
import { fileURLToPath } from 'url';
import path from 'path';
import fs from 'fs';
import os from 'os';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 加载 .env（CODEBUDDY_API_KEY / CODEBUDDY_MODEL 等）
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
// 流式调用护栏：CodeBuddy 不可达时避免长时间挂起（毫秒）。Java 侧读超时设为更大值。
const STREAM_TIMEOUT_MS = parseInt(process.env.CODEBUDDY_STREAM_TIMEOUT_MS || '90000', 10);
const COMPLETE_TIMEOUT_MS = parseInt(process.env.CODEBUDDY_COMPLETE_TIMEOUT_MS || '60000', 10);

app.use(express.json());

// 解析已安装的 codebuddy-code CLI（SDK 以子进程方式拉起它）
function resolveCodePath() {
  const p = path.join(__dirname, 'node_modules', '@tencent-ai', 'codebuddy-code', 'bin', 'codebuddy');
  return fs.existsSync(p) ? p : undefined;
}
const CODE_PATH = resolveCodePath();

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    sdk: !!CODE_PATH,
    hasKey: !!process.env.CODEBUDDY_API_KEY,
    model: MODEL,
    note: CODE_PATH ? 'CodeBuddy CLI 已就绪' : '未安装 @tencent-ai/codebuddy-code（npm install 即可）'
  });
});

// ---------- 提示词构造 ----------
function buildPrompt(userMessage, intentLabel, faqs, history) {
  const faqText = (faqs && faqs.length)
    ? faqs.map((f, i) => `${i + 1}. Q: ${f.question}\n   A: ${f.answer}`).join('\n')
    : '（无）';
  const sys =
    '你是一个电商平台的智能客服助手，请用中文、专业且亲切的语气回答用户问题。\n' +
    '请严格基于下方【知识库参考】作答；若参考内容不足以回答，请如实说明并建议用户回复「转人工」。\n' +
    '不要编造订单号、物流单号等具体数据。回答尽量分点、简洁。\n' +
    `【用户意图】${intentLabel || '未知'}\n` +
    '【知识库参考】\n' + faqText;

  const historyText = (history && history.length)
    ? history.map((m) => `${m.role === 'user' ? '用户' : '客服'}：${m.content}`).join('\n')
    : '';
  const prompt = (historyText ? historyText + '\n' : '') + '用户：' + userMessage;
  return { sys, prompt };
}

// 把一段文本切成小块，制造平滑的“逐字流出”观感
function chunkText(s) {
  const out = [];
  let i = 0;
  while (i < s.length) { out.push(s.slice(i, i + 4)); i += 4; }
  return out;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------- 真正的流式接口 ----------
app.post('/api/agent/stream', async (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no');
  res.flushHeaders && res.flushHeaders();

  const { userMessage, intentLabel, faqs = [], history = [] } = req.body || {};
  if (!userMessage) {
    res.write(`data: ${JSON.stringify({ type: 'error', message: 'userMessage 不能为空' })}\n\n`);
    return res.end();
  }
  if (!CODE_PATH) {
    res.write(`data: ${JSON.stringify({ type: 'error', message: 'CodeBuddy CLI 未安装：请在 agent-bridge 目录执行 npm install @tencent-ai/codebuddy-code' })}\n\n`);
    return res.end();
  }

  const { sys, prompt } = buildPrompt(userMessage, intentLabel, faqs, history);
  let full = '';
  const sendToken = (t) => res.write(`data: ${JSON.stringify({ type: 'token', text: t })}\n\n`);

  let settled = false;
  const guard = setTimeout(() => {
    if (settled || res.writableEnded) return;
    console.error(`[Bridge] CodeBuddy 流式调用超时（${STREAM_TIMEOUT_MS}ms），转 error 事件由 Java 降级`);
    res.write(`data: ${JSON.stringify({ type: 'error', message: 'CodeBuddy 调用超时' })}\n\n`);
    res.end();
  }, STREAM_TIMEOUT_MS);

  try {
    console.log(`[Bridge] 调用 CodeBuddy(${MODEL}) 流式生成，prompt 长度=${prompt.length}`);
    const stream = query({
      prompt,
      pathToCodebuddyCode: CODE_PATH,
      options: {
        model: MODEL,
        maxTurns: 1,
        systemPrompt: sys,
        permissionMode: 'bypassPermissions',
        cwd: os.tmpdir() // 避免扫描项目目录
      }
    });

    for await (const msg of stream) {
      if (msg.type === 'assistant') {
        const c = msg.message && msg.message.content;
        let text = '';
        if (typeof c === 'string') text = c;
        else if (Array.isArray(c)) for (const b of c) if (b && b.type === 'text') text += b.text;
        if (text) {
          full += text;
          for (const piece of chunkText(text)) { sendToken(piece); await sleep(8); }
        }
      } else if (msg.type === 'stream_event') {
        const d = msg.delta && msg.delta.text;
        if (d) { full += d; sendToken(d); }
      } else if (msg.type === 'error') {
        const m = (msg.error && (msg.error.message || msg.error)) || 'CodeBuddy 返回错误';
        console.error('[Bridge] CodeBuddy 流式错误:', m);
        clearTimeout(guard); settled = true;
        res.write(`data: ${JSON.stringify({ type: 'error', message: m })}\n\n`);
        return res.end();
      }
    }
    clearTimeout(guard); settled = true;
    res.write(`data: ${JSON.stringify({ type: 'done', text: full })}\n\n`);
    res.end();
  } catch (e) {
    clearTimeout(guard); settled = true;
    const msg = (e && e.message) || String(e);
    console.error('[Bridge] CodeBuddy 流式调用失败:', msg);
    res.write(`data: ${JSON.stringify({ type: 'error', message: msg })}\n\n`);
    res.end();
  }
});

// ---------- 非流式兜底（Java 侧 generate 用） ----------
app.post('/api/agent/complete', async (req, res) => {
  const { userMessage, intentLabel, faqs = [], history = [] } = req.body || {};
  if (!userMessage) return res.status(400).json({ error: 'userMessage 不能为空' });
  if (!CODE_PATH) return res.status(502).json({ error: 'CodeBuddy CLI 未安装' });

  const { sys, prompt } = buildPrompt(userMessage, intentLabel, faqs, history);
  let settled = false;
  const guard = setTimeout(() => {
    if (settled) return;
    settled = true;
    if (!res.headersSent) res.status(504).json({ error: 'CodeBuddy 调用超时' });
    else res.end();
  }, COMPLETE_TIMEOUT_MS);

  try {
    const stream = query({
      prompt,
      pathToCodebuddyCode: CODE_PATH,
      options: { model: MODEL, maxTurns: 1, systemPrompt: sys, permissionMode: 'bypassPermissions', cwd: os.tmpdir() }
    });
    let full = '';
    for await (const msg of stream) {
      if (msg.type === 'assistant') {
        const c = msg.message && msg.message.content;
        if (typeof c === 'string') full += c;
        else if (Array.isArray(c)) for (const b of c) if (b && b.type === 'text') full += b.text;
      } else if (msg.type === 'error') {
        const m = (msg.error && (msg.error.message || msg.error)) || 'CodeBuddy 返回错误';
        throw new Error(m);
      }
    }
    clearTimeout(guard); settled = true;
    res.json({ text: full || '抱歉，我暂时无法回答，请回复「转人工」联系人工客服。' });
  } catch (e) {
    clearTimeout(guard); settled = true;
    console.error('[Bridge] CodeBuddy 调用失败:', e && e.message);
    res.status(502).json({ error: (e && e.message) || 'CodeBuddy 调用失败' });
  }
});

app.listen(PORT, () => {
  console.log(`\n◉ CodeBuddy Agent 桥接服务已启动: http://localhost:${PORT}`);
  console.log(`  CODEBUDDY_API_KEY: ${process.env.CODEBUDDY_API_KEY ? '已配置' : '未配置（使用本机 CodeBuddy CN 登录态）'}`);
  console.log(`  CLI 就绪: ${CODE_PATH ? '是' : '否（未安装 @tencent-ai/codebuddy-code）'}`);
  console.log(`  流式超时护栏: ${STREAM_TIMEOUT_MS}ms\n`);
});
