import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'

describe('auth store skeleton', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

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
})
