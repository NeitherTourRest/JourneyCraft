import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import '@/styles/element-theme.scss'
import '@/styles/global.scss'
import App from './App.vue'
import router from './router'
import { useAuthStore } from '@/stores/auth'

const app = createApp(App)
app.use(createPinia())

// ★ 从 localStorage 恢复认证状态（必须在 router 之前）
const auth = useAuthStore()
auth.getStoredAuth()

app.use(router)
app.mount('#app')
