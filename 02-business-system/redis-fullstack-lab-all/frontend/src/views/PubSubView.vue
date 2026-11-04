<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { pubsubPublish, pubsubConsume } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import { ElMessage } from 'element-plus'

const from = ref('A')
const text = ref('')
const messages = ref<any[]>([])
const streamRecords = ref<any[]>([])
let es: EventSource | null = null

onMounted(() => {
  // SSE 实时通道：后端 RedisSubscriber 收到 PUB/SUB 消息后通过它推给所有前端
  es = new EventSource('/api/pubsub/stream')
  es.addEventListener('msg', (e: any) => {
    messages.value.push(JSON.parse(e.data))
  })
})
onUnmounted(() => {
  es?.close()
})

async function send() {
  if (!text.value) return
  await pubsubPublish(from.value, text.value)
  text.value = ''
}
async function consume() {
  const r = await pubsubConsume()
  streamRecords.value = r.data || []
  ElMessage.success('已从 Stream 消费组读取（可靠、不丢）')
}
</script>

<template>
  <DemoPanel
    title="场景5：发布订阅（PUB/SUB + Stream 对比）"
    :desc="`群聊通知、订单广播、配置热更新等场景。PUB/SUB：实时但「即发即弃」，无消费者会丢消息；Stream：带消费组、可重放、可 ack，适合做可靠消息队列。下方用 SSE 把 PUB/SUB 消息实时推到前端，开两个浏览器标签可看到 A 发 B 收。`"
  >
    <el-form inline>
      <el-form-item label="身份"><el-input v-model="from" style="width: 100px" /></el-form-item>
      <el-form-item label="消息"><el-input v-model="text" style="width: 280px" @keyup.enter="send" /></el-form-item>
      <el-form-item><el-button type="primary" @click="send">发送</el-button></el-form-item>
    </el-form>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="实时消息流（PUB/SUB via SSE）">
          <div class="chat">
            <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.body && JSON.parse(m.body).from === from ? 'mine' : ''">
              <b>{{ m.body ? JSON.parse(m.body).from : '?' }}</b>：{{ m.body ? JSON.parse(m.body).text : '' }}
              <small class="via">[{{ m.via }}]</small>
            </div>
            <div v-if="messages.length === 0" class="muted">还没有消息，发一条试试（或用两个标签模拟 A/B）</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="Stream 消费组（可靠队列）">
          <el-button @click="consume">消费一次（XREADGROUP + ACK）</el-button>
          <pre class="records">{{ JSON.stringify(streamRecords, null, 2) }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </DemoPanel>
</template>

<style scoped>
.chat { min-height: 200px; max-height: 320px; overflow: auto; background: #fafafa; padding: 8px; border-radius: 4px; }
.msg { padding: 4px 0; }
.msg.mine { color: #1890ff; text-align: right; }
.via { color: #999; }
.records { background: #f6f8fa; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 260px; overflow: auto; }
.muted { color: #999; font-size: 12px; }
</style>
