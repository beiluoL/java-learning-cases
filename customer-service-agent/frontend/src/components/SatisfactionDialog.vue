<template>
  <el-dialog v-model="visible" title="本次服务满意度评价" width="420px" :close-on-click-modal="false">
    <div style="text-align:center; padding: 8px 0 16px;">
      <el-rate v-model="score" :max="5" :texts="['很差','较差','一般','满意','非常满意']" show-text />
    </div>
    <el-input v-model="comment" type="textarea" :rows="3" placeholder="留下您的建议（选填）" />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { submitSatisfaction } from '../api/client';

const props = defineProps<{ modelValue: boolean; conversationId: string }>();
const emit = defineEmits<{ 'update:modelValue': [boolean]; submitted: [] }>();

const visible = ref(props.modelValue);
const score = ref(5);
const comment = ref('');

watch(() => props.modelValue, (v) => { visible.value = v; });
watch(visible, (v) => emit('update:modelValue', v));

async function submit() {
  try {
    await submitSatisfaction({ conversationId: props.conversationId, score: score.value, comment: comment.value });
    ElMessage.success('感谢您的评价！');
    visible.value = false;
    emit('submitted');
  } catch (e) {
    ElMessage.error('提交失败');
  }
}
</script>
