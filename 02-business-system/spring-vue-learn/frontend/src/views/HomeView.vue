<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const username = ref('')
const email = ref('')

// 进入页面即拉取用户列表
onMounted(() => {
  store.fetchUsers()
})

async function handleAdd() {
  if (!username.value.trim()) {
    return
  }
  await store.addUser({ username: username.value.trim(), email: email.value.trim() })
  username.value = ''
  email.value = ''
}
</script>

<template>
  <section class="home">
    <h2>用户列表</h2>

    <form class="form" @submit.prevent="handleAdd">
      <input v-model="username" placeholder="用户名" />
      <input v-model="email" placeholder="邮箱" />
      <button type="submit">新增</button>
    </form>

    <p v-if="store.loading">加载中...</p>
    <ul v-else>
      <li v-for="u in store.users" :key="u.id">
        #{{ u.id }} {{ u.username }} <span class="email">{{ u.email }}</span>
      </li>
      <li v-if="store.users.length === 0" class="empty">暂无数据，先新增一个吧</li>
    </ul>
  </section>
</template>

<style scoped>
.home {
  padding: 24px;
}
.form {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.form input {
  padding: 6px 10px;
  border: 1px solid #ccc;
  border-radius: 6px;
}
.form button {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
  cursor: pointer;
}
.email {
  color: #888;
  margin-left: 8px;
}
.empty {
  color: #999;
}
</style>
