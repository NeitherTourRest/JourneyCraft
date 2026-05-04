<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'

const sidebarCollapsed = ref(false)
const mobileSidebarVisible = ref(false)
const isMobile = ref(false)
const isTablet = ref(false)

function checkBreakpoint() {
  isMobile.value = window.innerWidth < 768
  isTablet.value = window.innerWidth >= 768 && window.innerWidth < 1024
  if (!isMobile.value) {
    mobileSidebarVisible.value = false
  }
  // Tablet starts collapsed (icon-only)
  if (isTablet.value) {
    sidebarCollapsed.value = true
  }
}

function toggleSidebar() {
  if (isMobile.value) {
    mobileSidebarVisible.value = !mobileSidebarVisible.value
  } else {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

onMounted(() => {
  checkBreakpoint()
  window.addEventListener('resize', checkBreakpoint)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkBreakpoint)
})
</script>

<template>
  <el-container class="app-layout">
    <AppSidebar
      :collapsed="sidebarCollapsed"
      :mobile-visible="mobileSidebarVisible"
      @toggle="toggleSidebar"
      @close-mobile="mobileSidebarVisible = false"
    />
    <el-container class="app-main-container">
      <AppHeader @toggle-sidebar="toggleSidebar" />
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
}

.app-main-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.app-main {
  flex: 1;
  overflow: auto;
  padding: 20px;
  background: var(--el-bg-color-page, #f2f3f5);
}
</style>
