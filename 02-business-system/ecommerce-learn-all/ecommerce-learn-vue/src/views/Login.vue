<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 style="text-align:center;color:var(--brand)">电商学习项目</h2>
      <el-tabs v-model="mode">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="70px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-20 位" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <el-button type="primary" style="width:100%" :loading="loading" @click="submit">
        {{ mode === 'login' ? '登录' : '注册并登录' }}
      </el-button>
      <p class="tip">演示：先注册普通用户，再用 SQL 将角色改为 ADMIN 即可进入后台。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login as loginApi, register as registerApi } from '../api/user'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const mode = ref('login')
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '', nickname: '', phone: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    if (mode.value === 'register') {
      await registerApi({ ...form })
      ElMessage.success('注册成功')
    }
    const data = await loginApi({ username: form.username, password: form.password })
    user.setLogin(data)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/products'
    router.push(redirect)
  } catch (e) {
    /* 错误已在拦截器提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding-top: 60px;
}
.login-card {
  width: 380px;
}
.tip {
  font-size: 12px;
  color: #999;
  margin-top: 12px;
}
</style>
