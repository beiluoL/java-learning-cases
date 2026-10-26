<template>
  <div>
    <div class="page-title">个人中心</div>
    <el-card class="uc-card">
      <el-descriptions :column="1" border v-if="!editing">
        <el-descriptions-item label="用户名">{{ user.userInfo?.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ user.userInfo?.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ user.userInfo?.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ user.userInfo?.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ user.userInfo?.role }}</el-descriptions-item>
      </el-descriptions>
      <el-form v-else :model="form" label-width="80px">
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="头像"><el-input v-model="form.avatar" placeholder="图片URL" /></el-form-item>
      </el-form>
      <div class="actions">
        <el-button v-if="!editing" @click="startEdit">编辑资料</el-button>
        <template v-else>
          <el-button type="primary" @click="save">保存</el-button>
          <el-button @click="editing = false">取消</el-button>
        </template>
      </div>
    </el-card>
    <div class="links">
      <el-button @click="$router.push('/orders')">我的订单</el-button>
      <el-button v-if="user.role === 'ADMIN'" type="warning" @click="$router.push('/admin/dashboard')">后台管理</el-button>
      <el-button @click="logout">退出登录</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { updateProfile } from '../api/user'
import { useUserStore } from '../store/user'

const router = useRouter()
const user = useUserStore()
const editing = ref(false)
const form = reactive({ nickname: '', phone: '', email: '', avatar: '' })

function startEdit() {
  const u = user.userInfo || {}
  form.nickname = u.nickname || ''
  form.phone = u.phone || ''
  form.email = u.email || ''
  form.avatar = u.avatar || ''
  editing.value = true
}
async function save() {
  await updateProfile(form)
  ElMessage.success('已保存')
  // 刷新本地资料
  user.userInfo = { ...user.userInfo, ...form }
  localStorage.setItem('userInfo', JSON.stringify(user.userInfo))
  editing.value = false
}
function logout() {
  user.logout()
  router.push('/login')
}
</script>

<style scoped>
.uc-card {
  max-width: 480px;
}
.actions {
  margin-top: 16px;
}
.links {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}
</style>
