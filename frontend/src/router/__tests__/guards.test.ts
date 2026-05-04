/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routes } from '../routes'

describe('router auth guards', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function createTestRouter() {
    const router = createRouter({
      history: createWebHashHistory(),
      routes,
    })

    router.beforeEach((to, _from, next) => {
      const auth = useAuthStore()
      const isAuth = auth.isAuthenticated

      if (to.meta.requiresAuth === true && !isAuth) {
        next('/login')
        return
      }

      if (to.meta.requiresAuth === false && isAuth) {
        next('/')
        return
      }

      next()
    })

    return router
  }

  it('unauthenticated user accessing /scenic is redirected to /login', async () => {
    const router = createTestRouter()
    await router.push('/scenic')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('authenticated user accessing /scenic is allowed', async () => {
    const auth = useAuthStore()
    auth.setAuth({ token: 'tok', refreshToken: 'ref', user: { id: 1, username: 'u', nickname: 'n', avatarUrl: null } })

    const router = createTestRouter()
    await router.push('/scenic')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/scenic')
  })

  it('authenticated user accessing /login is redirected to /', async () => {
    const auth = useAuthStore()
    auth.setAuth({ token: 'tok', refreshToken: 'ref', user: { id: 1, username: 'u', nickname: 'n', avatarUrl: null } })

    const router = createTestRouter()
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/scenic')
  })

  it('unknown route /nonexistent redirects to /404', async () => {
    const router = createTestRouter()
    await router.push('/nonexistent')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/404')
  })

  it('unauthenticated user accessing /register is allowed', async () => {
    const router = createTestRouter()
    await router.push('/register')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('authenticated user accessing /register is redirected to /', async () => {
    const auth = useAuthStore()
    auth.setAuth({ token: 'tok', refreshToken: 'ref', user: { id: 1, username: 'u', nickname: 'n', avatarUrl: null } })

    const router = createTestRouter()
    await router.push('/register')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/scenic')
  })
})
