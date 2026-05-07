<template>
  <div class="not-found-page">
    <!-- Background decorative gradient circles -->
    <div class="bg-decor" aria-hidden="true">
      <div class="bg-dot" style="top: 12%; left: 6%; width: 90px; height: 90px; animation-delay: 0s;"></div>
      <div class="bg-dot" style="top: 55%; right: 8%; width: 60px; height: 60px; animation-delay: 1.2s;"></div>
      <div class="bg-dot" style="bottom: 18%; left: 12%; width: 70px; height: 70px; animation-delay: 2.4s;"></div>
      <div class="bg-dot" style="top: 30%; right: 20%; width: 40px; height: 40px; animation-delay: 0.6s;"></div>
    </div>

    <!-- CSS Compass / Globe illustration -->
    <div class="compass-wrap" aria-hidden="true">
      <div class="compass-outer">
        <div class="compass-ring">
          <div class="compass-dash"></div>
        </div>
        <div class="compass-face">
          <div class="compass-cross">
            <div class="cross-arm n"></div>
            <div class="cross-arm s"></div>
            <div class="cross-arm e"></div>
            <div class="cross-arm w"></div>
          </div>
          <div class="compass-petal p1"></div>
          <div class="compass-petal p2"></div>
          <div class="compass-petal p3"></div>
          <div class="compass-petal p4"></div>
          <div class="compass-center"></div>
        </div>
      </div>
      <div class="compass-shimmer"></div>
    </div>

    <!-- Error content -->
    <h1 class="error-code">404</h1>
    <p class="error-subtitle">页面走丢了</p>
    <p class="error-desc">您访问的页面不存在或已被移除</p>

    <!-- Search section -->
    <div class="search-section">
      <el-input
        v-model="keyword"
        placeholder="搜索景点..."
        size="large"
        class="search-input"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon class="search-icon"><Search /></el-icon>
        </template>
        <template #append>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <!-- Hot destinations -->
    <div class="hot-section">
      <p class="hot-label">热门目的地</p>
      <div class="hot-tags">
        <el-tag
          v-for="item in hotList"
          :key="item.id"
          :color="tagBg"
          :text-color="tagText"
          round
          class="hot-tag"
          @click="goScenic(item.id)"
        >
          {{ item.name }}
        </el-tag>
      </div>
    </div>

    <!-- Return home button -->
    <el-button
      type="primary"
      size="large"
      round
      class="home-btn"
      @click="$router.push('/scenic')"
    >
      返回首页
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

const router = useRouter()
const keyword = ref('')

const tagBg = '#E1FBF7'
const tagText = '#1CB8A3'

const hotList = [
  { id: 1, name: '八达岭长城' },
  { id: 2, name: '故宫博物院' },
  { id: 3, name: '颐和园' },
  { id: 4, name: '天坛公园' },
  { id: 5, name: '圆明园' },
]

function handleSearch() {
  const q = keyword.value.trim()
  router.push(q ? { path: '/scenic', query: { q } } : '/scenic')
}

function goScenic(id: number) {
  router.push('/navigation/' + id)
}
</script>

<style scoped>
/* ── Page Container ── */
.not-found-page {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 120px);
  padding: 22px;
  background: #f5f0e8;
  overflow: hidden;
  animation: page-enter 0.15s ease-out forwards;
}

@keyframes page-enter {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── Background Decorative Dots (hidden) ── */
.bg-decor {
  display: none;
}

.bg-dot {
  display: none;
}

/* ── Sketch Compass (simplified) ── */
.compass-wrap {
  position: relative;
  width: 120px;
  height: 120px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compass-outer {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: #faf5ed;
  border: 3px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.compass-ring {
  display: none;
}

.compass-dash {
  display: none;
}

.compass-face {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #f5f0e8;
  border: 2px dashed #2c2c2c;
  display: flex;
  align-items: center;
  justify-content: center;
}

.compass-cross {
  position: absolute;
  inset: 0;
}

.cross-arm {
  position: absolute;
  background: #2c2c2c;
  border-radius: 0;
}

.cross-arm.n,
.cross-arm.s {
  width: 2px;
  height: 24px;
  left: 50%;
  transform: translateX(-50%);
}

.cross-arm.n { top: 4px; }
.cross-arm.s { bottom: 4px; }

.cross-arm.e,
.cross-arm.w {
  width: 24px;
  height: 2px;
  top: 50%;
  transform: translateY(-50%);
}

.cross-arm.e { right: 4px; }
.cross-arm.w { left: 4px; }

.compass-petal {
  display: none;
}

.compass-center {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2c2c2c;
  z-index: 1;
}

.compass-shimmer {
  display: none;
}

/* ── Error Text ── */
.error-code {
  font-size: 96px;
  font-weight: 800;
  color: #2c2c2c;
  line-height: 1;
  margin: 0 0 8px;
  letter-spacing: 4px;
  font-family: Georgia, 'Times New Roman', serif;
  animation: error-enter 0.15s ease-out 0.2s both;
  padding-bottom: 8px;
  border-bottom: 2px dashed #2c2c2c;
}

@keyframes error-enter {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.error-subtitle {
  font-size: 20px;
  font-weight: 600;
  color: #2c2c2c;
  margin: 0 0 8px;
  text-align: center;
  animation: content-enter 0.15s ease-out 0.35s both;
}

.error-desc {
  font-size: 15px;
  color: #2c2c2c;
  opacity: 0.7;
  margin: 0 0 32px;
  text-align: center;
  animation: content-enter 0.15s ease-out 0.5s both;
}

@keyframes content-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── Search Section ── */
.search-section {
  width: 100%;
  max-width: 460px;
  margin-bottom: 24px;
  animation: content-enter 0.15s ease-out 0.65s both;
}

.search-input {
  --el-input-border-radius: 4px;
  --el-input-focus-border-color: #2c2c2c;
  --el-input-hover-border-color: #2c2c2c;
  --el-input-bg-color: #faf5ed;
  --el-input-border-color: #2c2c2c;
  --el-input-text-color: #2c2c2c;
}

.search-input :deep(.el-input__wrapper) {
  box-shadow: none;
  border: 2px dashed #2c2c2c;
  border-radius: 4px 0 0 4px;
  background: #faf5ed;
  transition: box-shadow 0.12s ease;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.search-input :deep(.el-input-group__append) {
  border-radius: 0 4px 4px 0;
  overflow: hidden;
}

.search-input :deep(.el-input-group__append .el-button) {
  border-radius: 0 4px 4px 0;
  padding-left: 20px;
  padding-right: 20px;
}

.search-icon {
  font-size: 16px;
  color: #2c2c2c;
  opacity: 0.5;
}

/* ── Hot Destinations ── */
.hot-section {
  text-align: center;
  margin-bottom: 32px;
  animation: content-enter 0.15s ease-out 0.8s both;
}

.hot-label {
  font-size: 14px;
  color: #2c2c2c;
  opacity: 0.7;
  margin: 0 0 16px;
}

.hot-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  max-width: 400px;
}

.hot-tag {
  cursor: pointer;
  font-size: 14px;
  padding: 6px 18px;
  height: auto;
  line-height: 1.4;
  border-radius: 4px;
  transition: all 0.12s ease;
  user-select: none;
  background: #faf5ed !important;
  border: 2px dashed #2c2c2c !important;
  color: #2c2c2c !important;
}

.hot-tag:hover {
  opacity: 0.85;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

/* ── Home Button ── */
.home-btn {
  min-width: 180px;
  font-weight: 500;
  font-size: 16px;
  padding: 12px 36px;
  border-radius: 4px;
  animation: content-enter 0.15s ease-out 0.95s both;
  transition: all 0.12s ease;
  border: 2px dashed #2c2c2c !important;
}

.home-btn:hover {
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.home-btn:active {
  box-shadow: none;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .error-code {
    font-size: 72px;
  }

  .error-subtitle {
    font-size: 17px;
  }

  .error-desc {
    margin-bottom: 32px;
  }

  .search-section {
    max-width: 340px;
  }

  .compass-wrap {
    width: 90px;
    height: 90px;
  }

  .compass-outer {
    width: 80px;
    height: 80px;
  }

  .compass-face {
    width: 50px;
    height: 50px;
  }

  .home-btn {
    min-width: 150px;
    padding: 10px 28px;
  }
}
</style>
