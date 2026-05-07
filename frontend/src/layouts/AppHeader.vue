<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Menu, ArrowDown, User, UserFilled, SwitchButton,
  HomeFilled, Guide, Edit, Star, Clock,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

defineEmits<{
  'toggle-sidebar': []
}>()

const route = useRoute()
const auth = useAuthStore()

const activeRoute = computed(() => {
  const path = route.path
      if (path.startsWith('/navigation')) return '/navigation'
      if (path.startsWith('/diary')) return '/diary'
      return path
})

const username = computed(() => {
  return auth.nickname || auth.username || '未登录'
})

const avatarUrl = computed(() => {
  return undefined
})

const navTabs = [
  { path: '/scenic', icon: HomeFilled, label: '首页' },
  { path: '/navigation', icon: Guide, label: '导航' },
  { path: '/diary', icon: Edit, label: '日记' },
  { path: '/favorites', icon: Star, label: '收藏' },
  { path: '/history', icon: Clock, label: '历史' },
  { path: '/profile', icon: User, label: '我的' },
]

async function handleLogout() {
  try {
    await auth.logout()
  } catch {
    auth.clearAuth()
    localStorage.removeItem('journeycraft-auth')
    window.location.hash = '/login'
  }
  ElMessage.success('已退出登录')
}
</script>

<template>
  <header class="app-header">
    <div class="header-left">
      <el-button
        class="hamburger-btn"
        :icon="Menu"
        text
        @click="$emit('toggle-sidebar')"
      />
      <span class="brand-text">JourneyCraft</span>
    </div>

    <nav class="header-nav">
      <router-link
        v-for="tab in navTabs"
        :key="tab.path"
        :to="tab.path"
        class="nav-link"
        :class="{ active: activeRoute === tab.path }"
      >
        <el-icon class="nav-icon">
          <component :is="tab.icon" />
        </el-icon>
        <span class="nav-label">{{ tab.label }}</span>
      </router-link>
    </nav>

    <div class="header-right">
      <el-dropdown trigger="click" v-if="auth.isAuthenticated">
        <div class="user-info">
          <el-avatar :size="32" :src="avatarUrl" class="user-avatar">
            {{ username.charAt(0) }}
          </el-avatar>
          <span class="username">{{ username }}</span>
          <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item>
              <div class="dropdown-item-content">
                <el-icon><User /></el-icon>
                <span>个人中心</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">
              <div class="dropdown-item-content">
                <el-icon><SwitchButton /></el-icon>
                <span style="color: var(--el-color-danger)">退出登录</span>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <div class="user-info" v-else>
        <el-avatar :size="32" class="user-avatar">
          <el-icon><UserFilled /></el-icon>
        </el-avatar>
        <span class="username">未登录</span>
      </div>
    </div>
  </header>
</template>

<style scoped>
/* ──────────────────────────────────────────────
   Header — Sketch Style
   ────────────────────────────────────────────── */
.app-header {
  position: sticky;
  top: 0;
  z-index: 1000;

  display: flex;
  align-items: center;
  justify-content: space-between;

  height: 56px;
  padding: 0 16px;

  background: #f5f0e8;
  border-bottom: 2px dashed #2c2c2c;
  box-shadow: 0 2px 0 rgba(44, 44, 44, 0.15);
  border-radius: 0;

  transition: all 0.15s ease;
}

/* ──────────────────────────────────────────────
   Left — Hamburger + Brand
   ────────────────────────────────────────────── */
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.hamburger-btn {
  display: none;
  font-size: 20px;
  padding: 4px;
}

.hamburger-btn:hover {
  color: #2c2c2c;
}

.brand-text {
  font-size: 18px;
  font-weight: 700;
  color: #2c2c2c;
  letter-spacing: 0.5px;
  white-space: nowrap;
  user-select: none;
}

/* ──────────────────────────────────────────────
   Center — Navigation Tabs
   ────────────────────────────────────────────── */
.header-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 100%;
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 14px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  text-decoration: none;
  border-radius: 4px;
  transition: all 0.15s ease;
  cursor: pointer;
  white-space: nowrap;
}

.nav-link:hover {
  background: #e8e2d8;
  color: #2c2c2c;
}

.nav-link.active {
  color: #2c2c2c;
  font-weight: 600;
}

.nav-link.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 10px;
  right: 10px;
  height: 0;
  border-bottom: 2px dashed #2c2c2c;
}

.nav-icon {
  font-size: 16px;
}

.nav-label {
  line-height: 1;
}

/* ──────────────────────────────────────────────
   Right — User Dropdown
   ────────────────────────────────────────────── */
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.user-avatar {
  flex-shrink: 0;
}

.username {
  font-size: 14px;
  color: #2c2c2c;
  white-space: nowrap;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.dropdown-icon {
  font-size: 12px;
  color: #6b6b6b;
}

.dropdown-item-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ──────────────────────────────────────────────
   Responsive
   ────────────────────────────────────────────── */

/* Tablet (768px – 1023px): icons hidden, hamburger visible */
@media (min-width: 768px) and (max-width: 1023px) {
  .hamburger-btn {
    display: inline-flex;
  }

  .nav-icon {
    display: none;
  }
}

/* Mobile (<768px): nav tabs hidden, only hamburger */
@media (max-width: 767px) {
  .hamburger-btn {
    display: inline-flex;
  }

  .header-nav {
    display: none;
  }

  .username {
    display: none;
  }
}

/* Desktop (≥1024px): full display */
@media (min-width: 1024px) {
  .hamburger-btn {
    display: none;
  }
}
</style>

<!-- Non-scoped styles for Element Plus teleported dropdown menu -->
<style>
.el-dropdown-menu {
  background: #f5f0e8 !important;
  border: 2px dashed #2c2c2c !important;
  border-radius: 4px !important;
  box-shadow: 2px 2px 0 rgba(44, 44, 44, 0.3) !important;
  padding: 4px !important;
}

.el-dropdown-menu__item {
  color: #2c2c2c !important;
  border-radius: 4px !important;
  font-size: 14px !important;
}

.el-dropdown-menu__item:hover,
.el-dropdown-menu__item:focus {
  background: #e8e2d8 !important;
  color: #2c2c2c !important;
}

.el-dropdown-menu__item--divided {
  border-top: 2px dashed #2c2c2c !important;
}
</style>
