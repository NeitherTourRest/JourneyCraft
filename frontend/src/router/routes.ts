import type { RouteRecordRaw } from 'vue-router'

const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const NavigationView = () => import('@/views/NavigationView.vue')
const ScenicView = () => import('@/views/ScenicView.vue')
const ProfileView = () => import('@/views/ProfileView.vue')
const DiaryView = () => import('@/views/DiaryView.vue')
const DiaryDetailView = () => import('@/views/DiaryDetailView.vue')
const FavoritesView = () => import('@/views/FavoritesView.vue')
const HistoryView = () => import('@/views/HistoryView.vue')
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
    component: ScenicView,
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
    component: DiaryView,
    meta: { requiresAuth: true, title: '日记' },
  },
  {
    path: '/diary/:id',
    name: 'DiaryDetail',
    component: DiaryDetailView,
    meta: { requiresAuth: true, title: '日记详情' },
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: FavoritesView,
    meta: { requiresAuth: true, title: '收藏' },
  },
  {
    path: '/history',
    name: 'History',
    component: HistoryView,
    meta: { requiresAuth: true, title: '历史' },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: ProfileView,
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
