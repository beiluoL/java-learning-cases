import type { ChatMessage, FaqRef, SessionSummary, ConversationDetail } from '../types';

const BASE = '/api';

export interface ChatEvent {
  type: string;
  data: any;
}

/**
 * 以 fetch + ReadableStream 消费后端 SSE（text/event-stream）。
 * 支持 POST 请求体（EventSource 不支持），逐行解析 `data: ` 帧。
 * 返回一个取消函数（调用即中断流式读取）。
 */
export function streamChat(
  payload: { conversationId?: string; message: string; forceEscalate?: boolean },
  onEvent: (e: ChatEvent) => void,
  onError?: (msg: string) => void
): () => void {
  const controller = new AbortController();

  (async () => {
    try {
      const resp = await fetch(`${BASE}/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
      if (!resp.ok || !resp.body) {
        onError?.(`请求失败: ${resp.status}`);
        return;
      }
      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // 按行分割
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed.startsWith('data:')) continue;
          const json = trimmed.slice(5).trim();
          if (!json) continue;
          try {
            const evt = JSON.parse(json) as ChatEvent;
            onEvent(evt);
          } catch (e) {
            // 忽略无法解析的帧
          }
        }
      }
    } catch (e: any) {
      if (e.name !== 'AbortError') onError?.(e?.message || '流读取异常');
    }
  })();

  return () => controller.abort();
}

export async function getSessions(): Promise<SessionSummary[]> {
  const r = await fetch(`${BASE}/sessions`);
  return r.json();
}

export async function getAdminConversations(params: Record<string, any> = {}): Promise<{
  content: SessionSummary[];
  total: number;
  page: number;
  size: number;
}> {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  const r = await fetch(`${BASE}/admin/conversations?${qs.toString()}`);
  return r.json();
}

export async function getConversationDetail(id: string): Promise<ConversationDetail> {
  const r = await fetch(`${BASE}/admin/conversations/${id}`);
  return r.json();
}

export async function getOverview(): Promise<any> {
  const r = await fetch(`${BASE}/admin/stats/overview`);
  return r.json();
}

export async function getSatisfaction(): Promise<any> {
  const r = await fetch(`${BASE}/admin/stats/satisfaction`);
  return r.json();
}

export async function submitSatisfaction(req: {
  conversationId: string;
  score: number;
  comment?: string;
}): Promise<void> {
  await fetch(`${BASE}/satisfaction`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  });
}

export async function deleteConversation(id: string): Promise<void> {
  await fetch(`${BASE}/sessions/${id}`, { method: 'DELETE' });
}
