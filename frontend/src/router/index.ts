import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routes } from './routes'

export { routes } from './routes'

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  const isAuth = auth.isAuthenticated

  // Route requires auth but user is not authenticated
  if (to.meta.requiresAuth === true && !isAuth) {
    next('/login')
    return
  }

  // Route is guest-only (requiresAuth: false) and user is already authenticated
  if (to.meta.requiresAuth === false && isAuth) {
    next('/')
    return
  }

  next()
})

export default router
