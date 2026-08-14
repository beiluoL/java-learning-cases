export type IntentType = 'REFUND' | 'ORDER_QUERY' | 'TECH_SUPPORT' | 'UNKNOWN';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  intent?: IntentType;
  intentLabel?: string;
  confidence?: number;
  matchedKeywords?: string;
  escalated?: boolean;
  faqs?: FaqRef[];
  streaming?: boolean;
}

export interface FaqRef {
  id: number;
  question: string;
  answer: string;
  score: number;
}

export interface SessionSummary {
  id: string;
  title: string;
  status: string;
  intent: string | null;
  escalated: boolean;
  messageCount: number;
  satisfactionScore: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface MessageRecord {
  id: string;
  conversationId: string;
  role: string;
  content: string;
  intent: string | null;
  faqIds: string | null;
  escalated: boolean;
  seq: number;
  createdAt: string;
}

export interface ConversationDetail {
  conversation: {
    id: string;
    title: string;
    status: string;
    intent: string | null;
    escalated: boolean;
    satisfactionScore: number | null;
    satisfactionComment: string | null;
    createdAt: string;
    updatedAt: string;
  };
  messages: MessageRecord[];
  ticket: {
    id: string;
    reason: string;
    intent: string;
    status: string;
    summary: string;
    createdAt: string;
  } | null;
}
