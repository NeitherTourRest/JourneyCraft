import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserBrief } from '@/types/auth'
import { request } from '@/api/request'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<UserBrief | null>(null)

  const isAuthenticated = computed(() => !!token.value)

  function setAuth(data: { token: string; refreshToken: string; user: UserBrief }) {
    token.value = data.token
    refreshToken.value = data.refreshToken
    user.value = data.user
    persistToStorage()
  }

  function clearAuth() {
    token.value = null
    refreshToken.value = null
    user.value = null
  }

  // ── localStorage persistence ──
  const STORAGE_KEY = 'journeycraft-auth'

  function persistToStorage() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      token: token.value,
      refreshToken: refreshToken.value,
      user: user.value,
    }))
  }

  function clearStorage() {
    localStorage.removeItem(STORAGE_KEY)
  }

  // ── Auth operations ──

  async function login(username: string, password: string) {
    const res = await request.post<{ token: string; refreshToken: string; expiresIn: number; user: UserBrief }>(
      '/api/auth/login',
      { username, password },
    )
    if (res.data.code !== 200) throw new Error(res.data.message || 'Login failed')
    setAuth({
      token: res.data.data.token,
      refreshToken: res.data.data.refreshToken,
      user: res.data.data.user,
    })
    persistToStorage()
  }

  async function logout() {
    try {
      await request.post('/api/auth/logout')
    } catch {
      // Fire-and-forget: server may be down, still clear local state
    }
    clearAuth()
    clearStorage()
    window.location.hash = '/login'
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('No refresh token')
    const res = await request.post<{ token: string }>('/api/auth/refresh', { refreshToken: refreshToken.value })
    if (res.data.code !== 200) throw new Error(res.data.message || 'Refresh failed')
    token.value = res.data.data.token
    persistToStorage()
  }

  function getStoredAuth(): boolean {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (!stored) return false
      const data = JSON.parse(stored)
      if (!data.token || typeof data.token !== 'string') return false
      token.value = data.token
      refreshToken.value = data.refreshToken || null
      user.value = data.user || null
      return true
    } catch {
      clearStorage()
      return false
    }
  }

  return {
    token,
    refreshToken,
    user,
    isAuthenticated,
    setAuth,
    clearAuth,
    login,
    logout,
    refresh,
    getStoredAuth,
  }
})
