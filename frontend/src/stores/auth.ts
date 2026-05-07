import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request } from '@/api/request'

export const useAuthStore = defineStore('auth', () => {
  const userId = ref<number | null>(null)
  const username = ref<string | null>(null)
  const nickname = ref<string | null>(null)

  const isAuthenticated = ref(false)

  function setAuth(data: { userId: number; username: string; nickname: string; avatarUrl?: string }) {
    userId.value = data.userId
    username.value = data.username
    nickname.value = data.nickname
    isAuthenticated.value = true
    persistToStorage()
  }

  function clearAuth() {
    userId.value = null
    username.value = null
    nickname.value = null
    isAuthenticated.value = false
    clearStorage()
  }

  // ── localStorage persistence ──
  const STORAGE_KEY = 'journeycraft-auth'

  function persistToStorage() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      userId: userId.value,
      username: username.value,
      nickname: nickname.value,
    }))
  }

  function clearStorage() {
    localStorage.removeItem(STORAGE_KEY)
  }

  // ── Auth operations ──

  async function login(loginUsername: string, password: string) {
    const res = await request.post<{ userId: number; username: string; nickname: string; avatarUrl: string }>(
      '/api/auth/login',
      { username: loginUsername, password },
    )
    if (res.data.code !== 200) throw new Error(res.data.message || 'Login failed')
    const d = res.data.data
    setAuth({
      userId: d.userId,
      username: d.username,
      nickname: d.nickname,
      avatarUrl: d.avatarUrl,
    })
  }

  async function logout() {
    try {
      await request.post('/api/auth/logout')
    } catch {
      // Fire-and-forget
    }
    clearAuth()
    window.location.hash = '/login'
  }

  function getStoredAuth(): boolean {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (!stored) return false
      const data = JSON.parse(stored)
      if (!data.userId || typeof data.userId !== 'number') return false
      userId.value = data.userId
      username.value = data.username || null
      nickname.value = data.nickname || null
      isAuthenticated.value = true
      return true
    } catch {
      clearStorage()
      return false
    }
  }

  return {
    userId,
    username,
    nickname,
    isAuthenticated,
    setAuth,
    clearAuth,
    login,
    logout,
    getStoredAuth,
  }
})
