import { defineStore } from 'pinia'
import { ref } from 'vue'

// 用户状态：token / 角色 / 资料，持久化到 localStorage
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const role = ref(localStorage.getItem('role') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  function setLogin(data) {
    token.value = data.accessToken
    role.value = data.user.role
    userInfo.value = data.user
    localStorage.setItem('token', data.accessToken)
    localStorage.setItem('role', data.user.role)
    localStorage.setItem('userInfo', JSON.stringify(data.user))
  }

  function logout() {
    token.value = ''
    role.value = ''
    userInfo.value = null
    localStorage.clear()
  }

  return { token, role, userInfo, setLogin, logout }
})
