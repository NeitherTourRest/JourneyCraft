import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Use vi.hoisted to avoid hoisting issues with vi.mock
const { mockPost, mockGet, mockPut, mockDelete } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockGet: vi.fn(),
  mockPut: vi.fn(),
  mockDelete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  request: {
    post: mockPost,
    get: mockGet,
    put: mockPut,
    delete: mockDelete,
  },
}))

import { useAuthStore } from '../auth'

const STORAGE_KEY = 'journeycraft-auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  // ── Existing skeleton tests ──
  it('initializes with token as null', () => {
    const store = useAuthStore()
    expect(store.token).toBeNull()
  })

  it('isAuthenticated returns false when token is null', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
  })

  it('isAuthenticated returns true when token has value', () => {
    const store = useAuthStore()
    store.setAuth({ token: 'test-token', refreshToken: 'ref', user: { id: 1, username: 'u', nickname: 'n', avatarUrl: null } })
    expect(store.isAuthenticated).toBe(true)
  })

  it('setAuth updates all state fields', () => {
    const store = useAuthStore()
    const user = { id: 1, username: 'test', nickname: 'Test', avatarUrl: null }
    store.setAuth({ token: 'tok', refreshToken: 'ref', user })
    expect(store.token).toBe('tok')
    expect(store.refreshToken).toBe('ref')
    expect(store.user).toEqual(user)
  })

  it('clearAuth resets all state to null', () => {
    const store = useAuthStore()
    const user = { id: 1, username: 'test', nickname: 'Test', avatarUrl: null }
    store.setAuth({ token: 'tok', refreshToken: 'ref', user })
    store.clearAuth()
    expect(store.token).toBeNull()
    expect(store.refreshToken).toBeNull()
    expect(store.user).toBeNull()
  })

  // ── New tests ──

  it('login() calls POST /api/auth/login, stores token, persists to localStorage', async () => {
    const store = useAuthStore()
    mockPost.mockResolvedValueOnce({
      data: {
        code: 200,
        message: 'ok',
        data: {
          token: 'login-token',
          refreshToken: 'login-refresh',
          expiresIn: 3600,
          user: { id: 1, username: 'user1', nickname: 'User1', avatarUrl: null },
        },
      },
    })

    await store.login('user1', 'pass123')

    // State updated
    expect(store.token).toBe('login-token')
    expect(store.refreshToken).toBe('login-refresh')
    expect(store.user).toEqual({ id: 1, username: 'user1', nickname: 'User1', avatarUrl: null })
    expect(store.isAuthenticated).toBe(true)

    // Correct API call
    expect(mockPost).toHaveBeenCalledWith('/api/auth/login', { username: 'user1', password: 'pass123' })

    // Persisted to localStorage
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(stored.token).toBe('login-token')
    expect(stored.refreshToken).toBe('login-refresh')
    expect(stored.user).toEqual({ id: 1, username: 'user1', nickname: 'User1', avatarUrl: null })
  })

  it('login() failure throws error, does NOT set token', async () => {
    const store = useAuthStore()
    mockPost.mockResolvedValueOnce({
      data: { code: 401, message: 'Invalid credentials', data: null },
    })

    await expect(store.login('bad', 'wrong')).rejects.toThrow('Invalid credentials')
    expect(store.token).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('logout() calls POST /api/auth/logout, clears state + localStorage', async () => {
    const store = useAuthStore()
    store.setAuth({ token: 'tok', refreshToken: 'ref', user: { id: 1, username: 'u', nickname: 'n', avatarUrl: null } })
    mockPost.mockResolvedValueOnce({ data: { code: 200, message: 'ok', data: null } })

    await store.logout()

    expect(mockPost).toHaveBeenCalledWith('/api/auth/logout')
    expect(store.token).toBeNull()
    expect(store.refreshToken).toBeNull()
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('refresh() calls POST /api/auth/refresh with refreshToken, updates token', async () => {
    const store = useAuthStore()
    store.setAuth({ token: 'old-token', refreshToken: 'old-refresh', user: { id: 1, username: 'u', nickname: 'n', avatarUrl: null } })
    mockPost.mockResolvedValueOnce({
      data: { code: 200, message: 'ok', data: { token: 'new-token' } },
    })

    await store.refresh()

    expect(mockPost).toHaveBeenCalledWith('/api/auth/refresh', { refreshToken: 'old-refresh' })
    expect(store.token).toBe('new-token')
    // refreshToken and user should remain unchanged
    expect(store.refreshToken).toBe('old-refresh')
    expect(store.user).toEqual({ id: 1, username: 'u', nickname: 'n', avatarUrl: null })
  })

  it('getStoredAuth() reads from localStorage and restores state', () => {
    const store = useAuthStore()
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      token: 'stored-token',
      refreshToken: 'stored-refresh',
      user: { id: 2, username: 'stored', nickname: 'Stored', avatarUrl: null },
    }))

    const result = store.getStoredAuth()

    expect(result).toBe(true)
    expect(store.token).toBe('stored-token')
    expect(store.refreshToken).toBe('stored-refresh')
    expect(store.user).toEqual({ id: 2, username: 'stored', nickname: 'Stored', avatarUrl: null })
    expect(store.isAuthenticated).toBe(true)
  })
})
