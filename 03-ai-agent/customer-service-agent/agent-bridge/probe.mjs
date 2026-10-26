// 最小探针 v2：指向已安装的 @tencent-ai/codebuddy-code CLI，验证在 internal 环境下能否鉴权并流式产出 token。
import { query } from '@tencent-ai/agent-sdk';
import { appendFileSync, writeFileSync } from 'node:fs';

const LOG = '/tmp/probe.log';
writeFileSync(LOG, '');
const log = (s) => { appendFileSync(LOG, s + '\n'); };

const codePath = new URL('./node_modules/@tencent-ai/codebuddy-code/bin/codebuddy', import.meta.url).pathname;
log('[probe] codePath=' + codePath);
log('[probe] CODEBUDDY_INTERNET_ENVIRONMENT=' + (process.env.CODEBUDDY_INTERNET_ENVIRONMENT || '(unset)'));
log('[probe] CODEBUDDY_GATEWAY_AUTH=' + (process.env.CODEBUDDY_GATEWAY_AUTH || '(unset)'));

async function main() {
  log('[probe] calling query()...');
  const stream = query({
    prompt: '请用一句话介绍你自己，并说明你能做什么。',
    pathToCodebuddyCode: codePath,
    options: {
      model: process.env.CODEBUDDY_MODEL || 'claude-sonnet-4',
      maxTurns: 1,
      systemPrompt: '你是电商平台的智能客服助手，请用中文简洁回答。',
      permissionMode: 'bypassPermissions'
    }
  });

  let n = 0;
  let full = '';
  for await (const msg of stream) {
    if (msg.type === 'assistant') {
      const c = msg.message?.content;
      let text = '';
      if (typeof c === 'string') text = c;
      else if (Array.isArray(c)) for (const b of c) if (b?.type === 'text') text += b.text;
      if (text) { n++; full += text; if (n <= 40) log('TOKEN#' + n + ': ' + JSON.stringify(text)); }
    } else if (msg.type === 'result') {
      log('[probe] result subtype=' + (msg.subtype || '(none)'));
    } else if (msg.type === 'stream_event') {
      const d = msg.delta?.text;
      if (d) { n++; full += d; if (n <= 40) log('STREAM#' + n + ': ' + JSON.stringify(d)); }
    } else {
      log('[probe] msg.type=' + msg.type + ' :: ' + JSON.stringify(msg).slice(0, 800));
    }
  }
  log('[probe] DONE. tokenChunks=' + n + ' fullLen=' + full.length);
  log('[probe] FULL >>> ' + full);
}

main().catch((e) => {
  log('[probe] ERROR: ' + (e?.message || e));
  if (e?.stack) log(e.stack.split('\n').slice(0, 6).join('\n'));
  process.exit(1);
});
