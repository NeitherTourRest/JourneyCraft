import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserBrief } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<UserBrief | null>(null)

  const isAuthenticated = computed(() => !!token.value)

  function setAuth(data: { token: string; refreshToken: string; user: UserBrief }) {
    token.value = data.token
    refreshToken.value = data.refreshToken
    user.value = data.user
  }

  function clearAuth() {
    token.value = null
    refreshToken.value = null
    user.value = null
  }

  // Placeholder - will be implemented in T9
  async function login(_username: string, _password: string) {
    throw new Error('Not implemented in skeleton')
  }
  async function logout() {
    throw new Error('Not implemented in skeleton')
  }
  async function refresh() {
    throw new Error('Not implemented in skeleton')
  }
  function getStoredAuth() {
    throw new Error('Not implemented in skeleton')
  }

  return { token, refreshToken, user, isAuthenticated, setAuth, clearAuth, login, logout, refresh, getStoredAuth }
})
