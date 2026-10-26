import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useRealtimeStore } from '@/stores/realtime';
import type { AnalysisAlert, OpinionVO } from '@/api/types';

let client: Client | null = null;

/**
 * 建立实时通道：通过 SockJS + STOMP 连接后端 /ws，
 * 订阅设备指标、告警、舆情三个主题并写入实时状态。
 */
export function connectRealtime() {
  const store = useRealtimeStore();
  if (client && client.connected) {
    return;
  }
  client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 3000,
    onConnect: () => {
      store.setConnected(true);
      client?.subscribe('/topic/device-metrics', (message) => {
        store.upsertMetric(JSON.parse(message.body));
      });
      client?.subscribe('/topic/alert', (message) => {
        store.addAlert(JSON.parse(message.body) as AnalysisAlert);
      });
      client?.subscribe('/topic/opinion', (message) => {
        store.addOpinion(JSON.parse(message.body) as OpinionVO);
      });
    },
    onWebSocketClose: () => store.setConnected(false),
    onStompError: () => store.setConnected(false),
  });
  client.activate();
}

export function disconnectRealtime() {
  if (client) {
    void client.deactivate();
    client = null;
  }
}
