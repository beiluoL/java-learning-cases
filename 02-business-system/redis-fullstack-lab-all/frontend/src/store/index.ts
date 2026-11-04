import { defineStore } from 'pinia'

// 登录态：分布式会话场景用于在多实例间共享
export const useSessionStore = defineStore('session', {
  state: () => ({
    user: null as any
  }),
  actions: {
    setUser(u: any) {
      this.user = u
    },
    clear() {
      this.user = null
    }
  }
})
