import type { RouteRecordRaw } from 'vue-router'

const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const NavigationView = () => import('@/views/NavigationView.vue')
const PlaceholderPage = () => import('@/views/PlaceholderPage.vue')
const NotFound = () => import('@/views/NotFound.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/scenic',
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: RegisterView,
    meta: { requiresAuth: false, title: '注册' },
  },
  {
    path: '/scenic',
    name: 'Scenic',
    component: PlaceholderPage,
    meta: { requiresAuth: true, title: '景点' },
  },
  {
    path: '/navigation/:scenicId?',
    name: 'Navigation',
    component: NavigationView,
    meta: { requiresAuth: true, title: '导航' },
  },
  {
    path: '/diary',
    name: 'Diary',
    component: PlaceholderPage,
    meta: { requiresAuth: true, title: '日记' },
  },
  {
    path: '/diary/:id',
    name: 'DiaryDetail',
    component: PlaceholderPage,
    meta: { requiresAuth: true, title: '日记详情' },
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: PlaceholderPage,
    meta: { requiresAuth: true, title: '收藏' },
  },
  {
    path: '/history',
    name: 'History',
    component: PlaceholderPage,
    meta: { requiresAuth: true, title: '历史' },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: PlaceholderPage,
    meta: { requiresAuth: true, title: '个人中心' },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: NotFound,
    meta: { requiresAuth: false, title: '404' },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]
