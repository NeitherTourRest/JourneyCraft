<script setup lang="ts">
import { useRoute } from 'vue-router'
import {
  HomeFilled,
  Guide,
  Edit,
  Star,
  User,
} from '@element-plus/icons-vue'

interface TabItem {
  path: string
  icon: object
  label: string
}

const route = useRoute()

const tabs: TabItem[] = [
  { path: '/scenic', icon: HomeFilled, label: '首页' },
  { path: '/navigation', icon: Guide, label: '导航' },
  { path: '/diary', icon: Edit, label: '日记' },
  { path: '/favorites', icon: Star, label: '收藏' },
  { path: '/profile', icon: User, label: '我的' },
]

function isActive(path: string): boolean {
  return route.path.startsWith(path)
}
</script>

<template>
  <nav class="mobile-nav">
    <router-link
      v-for="item in tabs"
      :key="item.path"
      :to="item.path"
      class="nav-item"
      :class="{ active: isActive(item.path) }"
    >
      <el-icon :size="22">
        <component :is="item.icon" />
      </el-icon>
      <span class="nav-label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<style scoped lang="scss">
.mobile-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: flex;
  justify-content: space-around;
  align-items: center;
  height: 56px;
  padding-bottom: env(safe-area-inset-bottom, 0);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid var(--el-color-primary-light-8);
  border-radius: 16px 16px 0 0;

  // Only visible on mobile (< 768px)
  @media (min-width: 768px) {
    display: none;
  }
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  text-decoration: none;
  transition: color 0.2s, transform 0.2s;
  padding: 4px 0;

  &:hover {
    color: var(--el-color-primary);
  }

  &.active {
    color: var(--el-color-primary);
    transform: scale(1.1);

    .nav-label {
      font-weight: 500;
    }
  }
}

.nav-label {
  font-size: 10px;
  line-height: 1;
  transition: font-weight 0.2s;
}
</style>
