<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeFilled,
  Location,
  Guide,
  Edit,
  Star,
  Clock,
  User,
  Fold,
  Expand,
} from '@element-plus/icons-vue'

const props = defineProps<{
  collapsed: boolean
  mobileVisible: boolean
}>()

const emit = defineEmits<{
  toggle: []
  'close-mobile': []
}>()

const route = useRoute()

const isMobile = ref(false)
function onResize() {
  isMobile.value = window.innerWidth < 768
}
onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})

const menuCollapsed = computed(() => {
  // On mobile overlay, always show full text
  if (isMobile.value) return false
  return props.collapsed
})

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/navigation')) return '/navigation'
  if (path.startsWith('/diary')) return '/diary'
  return path
})

const menuItems = [
  { index: '/scenic', icon: HomeFilled, label: '首页' },
  { index: '/scenic', icon: Location, label: '景点' },
  { index: '/navigation', icon: Guide, label: '导航' },
  { index: '/diary', icon: Edit, label: '日记' },
  { index: '/favorites', icon: Star, label: '收藏' },
  { index: '/history', icon: Clock, label: '历史' },
  { index: '/profile', icon: User, label: '我的' },
]
</script>

<template>
  <aside
    class="app-sidebar"
    :class="{
      'is-collapsed': collapsed && !isMobile,
      'is-mobile-visible': mobileVisible && isMobile,
    }"
  >
    <!-- Mobile backdrop -->
    <div
      v-if="isMobile && mobileVisible"
      class="sidebar-backdrop"
      @click="emit('close-mobile')"
    />

    <div class="sidebar-inner">
      <div class="sidebar-logo">
        <span v-if="!menuCollapsed" class="logo-text">JourneyCraft</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="menuCollapsed"
        router
        class="sidebar-menu"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.index"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-collapse-btn" @click="emit('toggle')">
        <el-icon :size="18" :class="{ 'is-rotated': collapsed && !isMobile }">
          <Fold v-if="!collapsed || isMobile" />
          <Expand v-else />
        </el-icon>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.app-sidebar {
  --sidebar-width: 240px;
  --sidebar-collapsed-width: 64px;

  width: var(--sidebar-width);
  flex-shrink: 0;
  height: 100vh;
  position: sticky;
  top: 0;
  left: 0;
  background: #f5f0e8;
  border-right: 2px dashed #2c2c2c;
  transition: width 0.15s ease;
  overflow: hidden;
  z-index: 100;
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 2px dashed #2c2c2c;
  flex-shrink: 0;
  transition: border-color 0.15s ease;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #2c2c2c;
  letter-spacing: 1px;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none !important;
  padding: 4px 0;

  :deep(.el-menu) {
    background-color: transparent;
    border-right: none;
  }

  :deep(.el-menu-item) {
    margin: 2px 8px;
    border-radius: 4px;
    transition: all 0.15s ease;
    position: relative;
    color: #2c2c2c;
    background-color: transparent;

    &:hover {
      background-color: #e8e2d8;
    }

    &.is-active {
      background-color: #e8e2d8;
      color: #2c2c2c;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 4px;
        bottom: 4px;
        width: 0;
        border-right: 3px dashed #2c2c2c;
      }
    }
  }

  :deep(.el-sub-menu__title) {
    color: #2c2c2c;
    background-color: transparent;

    &:hover {
      background-color: #e8e2d8;
    }
  }
}

.sidebar-collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 2px dashed #2c2c2c;
  cursor: pointer;
  color: #2c2c2c;
  transition: background-color 0.15s, color 0.15s;
  flex-shrink: 0;
}

.sidebar-collapse-btn:hover {
  background: #e8e2d8;
  color: #2c2c2c;
}

.sidebar-collapse-btn .el-icon {
  transition: transform 0.15s ease;
}

.sidebar-collapse-btn .el-icon.is-rotated {
  transform: rotate(180deg);
}

.sidebar-backdrop {
  display: none;
}

/* Tablet: collapsed by default, expandable */
@media (min-width: 768px) and (max-width: 1023px) {
  .app-sidebar {
    width: var(--sidebar-collapsed-width);
  }

  .app-sidebar:not(.is-collapsed) {
    width: var(--sidebar-width);
  }
}

/* Mobile: overlay */
@media (max-width: 767px) {
  .app-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 2000;
    transform: translateX(-100%);
    width: var(--sidebar-width);
    transition: transform 0.15s ease;
  }

  .app-sidebar.is-mobile-visible {
    transform: translateX(0);
  }

  .sidebar-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: -1;
    animation: fade-in 0.15s ease;
  }
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
