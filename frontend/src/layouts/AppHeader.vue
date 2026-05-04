<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Menu } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

defineEmits<{
  'toggle-sidebar': []
}>()

const route = useRoute()
const auth = useAuthStore()

const pageTitle = computed(() => {
  return (route.meta.title as string) || 'JourneyCraft'
})

const username = computed(() => {
  return auth.user?.nickname || '未登录'
})

const avatarUrl = computed(() => {
  return auth.user?.avatarUrl || undefined
})
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
      <span class="page-title">{{ pageTitle }}</span>
    </div>
    <div class="header-right">
      <el-avatar :size="32" :src="avatarUrl" class="user-avatar">
        {{ username.charAt(0) }}
      </el-avatar>
      <span class="username">{{ username }}</span>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hamburger-btn {
  display: none;
  font-size: 20px;
  padding: 4px;
}

.page-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  flex-shrink: 0;
}

.username {
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  white-space: nowrap;
}

/* Show hamburger on tablet and mobile */
@media (max-width: 1023px) {
  .hamburger-btn {
    display: inline-flex;
  }
}
</style>
