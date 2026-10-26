import { defineStore } from 'pinia'
import { ref } from 'vue'
import { createUser, listUsers } from '@/api/user'
import type { User } from '@/types/user'

// Pinia 状态管理：集中保存用户列表与加载状态
export const useUserStore = defineStore('user', () => {
  const users = ref<User[]>([])
  const loading = ref(false)

  async function fetchUsers() {
    loading.value = true
    try {
      users.value = await listUsers()
    } finally {
      loading.value = false
    }
  }

  async function addUser(payload: { username: string; email: string }) {
    const created = await createUser(payload)
    users.value.push(created)
    return created
  }

  return { users, loading, fetchUsers, addUser }
})
