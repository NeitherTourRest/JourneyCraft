import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

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

describe('auth store (username auth)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('initializes with userId as null', () => {
    const store = useAuthStore()
    expect(store.userId).toBeNull()
  })

  it('isAuthenticated is false when no user', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
  })

  it('isAuthenticated is true after setAuth', () => {
    const store = useAuthStore()
    store.setAuth({ userId: 1, username: 'u', nickname: 'n' })
    expect(store.isAuthenticated).toBe(true)
  })

  it('setAuth updates all state fields', () => {
    const store = useAuthStore()
    store.setAuth({ userId: 1, username: 'test', nickname: 'Test' })
    expect(store.userId).toBe(1)
    expect(store.username).toBe('test')
    expect(store.nickname).toBe('Test')
  })

  it('clearAuth resets all state', () => {
    const store = useAuthStore()
    store.setAuth({ userId: 1, username: 'test', nickname: 'Test' })
    store.clearAuth()
    expect(store.userId).toBeNull()
    expect(store.username).toBeNull()
    expect(store.nickname).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('login() calls POST /api/auth/login, stores userId, persists to localStorage', async () => {
    const store = useAuthStore()
    mockPost.mockResolvedValueOnce({
      data: {
        code: 200,
        message: 'ok',
        data: { userId: 1, username: 'user1', nickname: 'User1', avatarUrl: null },
      },
    })

    await store.login('user1', 'pass123')

    expect(store.userId).toBe(1)
    expect(store.username).toBe('user1')
    expect(store.nickname).toBe('User1')
    expect(store.isAuthenticated).toBe(true)
    expect(mockPost).toHaveBeenCalledWith('/api/auth/login', { username: 'user1', password: 'pass123' })

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(stored.userId).toBe(1)
    expect(stored.username).toBe('user1')
    expect(stored.nickname).toBe('User1')
  })

  it('login() failure throws error, does NOT set user', async () => {
    const store = useAuthStore()
    mockPost.mockResolvedValueOnce({
      data: { code: 401, message: 'Invalid credentials', data: null },
    })

    await expect(store.login('bad', 'wrong')).rejects.toThrow('Invalid credentials')
    expect(store.userId).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('logout() calls POST /api/auth/logout, clears state + localStorage', async () => {
    const store = useAuthStore()
    store.setAuth({ userId: 1, username: 'u', nickname: 'n' })
    mockPost.mockResolvedValueOnce({ data: { code: 200, message: 'ok', data: null } })

    await store.logout()

    expect(mockPost).toHaveBeenCalledWith('/api/auth/logout')
    expect(store.userId).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('getStoredAuth() reads from localStorage and restores state', () => {
    const store = useAuthStore()
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      userId: 2,
      username: 'stored',
      nickname: 'Stored',
    }))

    const result = store.getStoredAuth()

    expect(result).toBe(true)
    expect(store.userId).toBe(2)
    expect(store.username).toBe('stored')
    expect(store.nickname).toBe('Stored')
    expect(store.isAuthenticated).toBe(true)
  })

  it('getStoredAuth() returns false for corrupted localStorage', () => {
    const store = useAuthStore()
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ invalid: true }))
    expect(store.getStoredAuth()).toBe(false)
  })
})
