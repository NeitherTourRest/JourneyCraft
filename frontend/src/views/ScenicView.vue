<template>
  <!-- ==================== HERO SECTION ==================== -->
  <div class="hero-section">
    <div class="hero-bg">
      <div class="hero-dot dot-1"></div>
      <div class="hero-dot dot-2"></div>
      <div class="hero-dot dot-3"></div>
      <div class="hero-dot dot-4"></div>
    </div>
    <div class="hero-content">
      <h1 class="hero-title">探索你的<br />下一个目的地</h1>
      <p class="hero-subtitle">从昌平出发，发现周边的精彩</p>
      <div class="hero-search">
        <el-input
          v-model="keyword"
          placeholder="搜索景点名称..."
          :prefix-icon="Search"
          clearable
          size="large"
          class="hero-search-input"
          @keyup.enter="searchScenic"
          @clear="searchScenic"
        >
          <template #append>
            <el-button :icon="Search" @click="searchScenic" :loading="loading" class="search-btn" />
          </template>
        </el-input>
      </div>
    </div>
    <div class="hero-wave"></div>
  </div>

  <!-- ==================== MAIN CONTENT ==================== -->
  <div class="page-container scenic-page">
    <!-- Skeleton Loading -->
    <div v-if="loading" class="scenic-grid">
      <el-card v-for="n in 6" :key="'skel-' + n" class="scenic-card" shadow="hover">
        <el-skeleton animated>
          <template #template>
            <div class="card-inner">
              <el-skeleton-item variant="image" class="skeleton-img" />
              <div class="card-content">
                <el-skeleton-item variant="h3" class="skeleton-title" />
                <el-skeleton-item variant="text" class="skeleton-line" />
                <el-skeleton-item variant="text" class="skeleton-line short" />
              </div>
            </div>
          </template>
        </el-skeleton>
      </el-card>
    </div>

    <!-- List -->
    <div v-else class="scenic-grid">
      <div v-if="list.length === 0" class="empty-wrapper">
        <el-empty description="暂无景点数据" />
      </div>

      <el-card
        v-for="(item, index) in list"
        :key="item.id"
        class="scenic-card"
        shadow="hover"
        :class="{ 'card-featured': index < 2 }"
      >
        <div class="card-inner">
          <!-- Gradient Image Placeholder -->
          <div class="card-image">
            <div class="image-placeholder" :class="{ 'gradient-alt': index % 2 === 1 }">
              <el-tag
                v-if="index < 3"
                class="hot-badge"
                size="small"
                effect="dark"
                type="danger"
              >
                热门
              </el-tag>
              <el-tag class="rating-badge" size="small" effect="dark" type="warning">
                <el-icon><StarFilled /></el-icon>
                {{ item.rating?.toFixed(1) || '-' }}
              </el-tag>
            </div>
          </div>

          <!-- Info Section -->
          <div class="card-content">
            <h3 class="card-name">{{ item.name }}</h3>

            <div class="card-meta">
              <span class="meta-item">
                <el-icon><Location /></el-icon>
                {{ item.city || '未知' }}
              </span>
              <span class="meta-item heat-score">热度 {{ item.heatScore || 0 }}</span>
              <span class="meta-item visit-count">{{ item.visitCount || 0 }} 次浏览</span>
            </div>

            <p class="card-desc" v-if="item.description">{{ item.description }}</p>

            <div class="card-footer">
              <div class="price-tag">
                <span class="price-currency">¥</span>
                <span class="price-value">{{ item.ticketPrice || 0 }}</span>
                <span class="price-suffix">起</span>
              </div>
              <el-button size="small" class="nav-btn" @click="goNavigate(item.id)">开始导航</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- Pagination -->
    <div class="pagination-wrap" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadList"
        class="scenic-pagination"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search, StarFilled, Location } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { scenicApi } from '@/api/modules/scenic'

const router = useRouter()
const keyword = ref('')
const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

onMounted(() => loadList())

async function loadList(p?: number) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = keyword.value
      ? await scenicApi.search({ keyword: keyword.value, page: page.value, size: size.value })
      : await scenicApi.getList({ page: page.value, size: size.value })
    if (res.data.code === 200) {
      list.value = res.data.data?.list || []
      total.value = res.data.data?.total || 0
    }
  } catch { list.value = [] }
  finally { loading.value = false }
}

function searchScenic() { page.value = 1; loadList() }
function goNavigate(id: number) { router.push('/navigation/' + id) }
</script>

<style scoped>
/* ================================================================
   HERO SECTION — Sketch Style Simple Header
   ================================================================ */
.hero-section {
  position: relative;
  min-height: auto;
  background: #f5f0e8;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
}

/* ── Decorative Floating Elements (hidden) ── */
.hero-bg {
  display: none;
}

.hero-dot {
  display: none;
}

/* ── Hero Content ── */
.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 0;
  max-width: 680px;
  width: 100%;
}

.hero-title {
  font-size: 28px;
  font-weight: 700;
  color: #2c2c2c;
  line-height: 1.3;
  margin: 0 0 12px;
  font-family: Georgia, 'Times New Roman', serif;
  letter-spacing: 1px;
  padding-bottom: 10px;
  display: inline-block;
  border-bottom: 2px dashed #2c2c2c;
  text-shadow: none;
}

.hero-subtitle {
  font-size: 15px;
  color: #2c2c2c;
  margin: 0 0 24px;
  font-weight: 400;
  opacity: 0.7;
  letter-spacing: 1px;
}

/* ── Hero Search ── */
.hero-search {
  max-width: 540px;
  margin: 0 auto;
}

.hero-search-input {
  --el-input-bg-color: #faf5ed;
  --el-input-border-color: #2c2c2c;
  --el-input-hover-border-color: #2c2c2c;
  --el-input-focus-border-color: #2c2c2c;
  --el-input-text-color: #2c2c2c;
  --el-input-placeholder-color: rgba(44,44,44,0.5);
}

.hero-search-input :deep(.el-input__wrapper) {
  background: #faf5ed;
  border: 2px dashed #2c2c2c;
  border-radius: 4px 0 0 4px;
  box-shadow: none;
  transition: all 0.15s ease;
}

.hero-search-input :deep(.el-input__wrapper.is-focus) {
  border-color: #2c2c2c;
  background: #f5f0e8;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.hero-search-input :deep(.el-input__inner) {
  color: #2c2c2c;
}

.hero-search-input :deep(.el-input__inner::placeholder) {
  color: rgba(44,44,44,0.5);
}

.hero-search .search-btn {
  border-radius: 0 4px 4px 0;
  --el-button-bg-color: #2c2c2c;
  --el-button-border-color: #2c2c2c;
  --el-button-hover-bg-color: #444;
  --el-button-hover-border-color: #444;
  --el-button-text-color: #f5f0e8;
  font-weight: 600;
  height: 40px;
}

/* ── Hero Bottom Wave (hidden) ── */
.hero-wave {
  display: none;
}

/* ================================================================
   PAGE LAYOUT
   ================================================================ */
.scenic-page {
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
  z-index: 2;
}

/* ================================================================
   GRID — 2-Column
   ================================================================ */
.scenic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 24px;
  min-height: 200px;
}

.empty-wrapper {
  grid-column: 1 / -1;
}

/* ================================================================
   CARD — Sketch Style
   ================================================================ */
.scenic-card {
  --el-card-padding: 0;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
  border: 2px dashed #2c2c2c;
  border-radius: 4px;
  background: #faf5ed;
}

.scenic-card:hover {
  box-shadow: 4px 4px 0px rgba(44,44,44,0.3);
}

.card-featured {
  position: relative;
}

.card-inner {
  display: flex;
  flex-direction: column;
}

/* ──────────────────────────────────────────────
   Image — Full-Width, 200px
   ────────────────────────────────────────────── */
.card-image {
  width: 100%;
  height: 200px;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}

.image-placeholder {
  position: absolute;
  inset: 0;
  background: #e8e2d8;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 2px dashed #2c2c2c;
}

.image-placeholder.gradient-alt {
  background: #e8e2d8;
}

/* Subtle inner shadow overlay for depth */
.card-image::after {
  display: none;
}

/* ── Badges ── */
.hot-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 2;
  font-weight: 600;
  border: 2px dashed #2c2c2c !important;
  background: #f5f0e8 !important;
  color: #2c2c2c !important;
  letter-spacing: 1px;
  padding: 4px 10px;
  border-radius: 2px;
}

.rating-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 2px dashed #2c2c2c !important;
  background: #f5f0e8 !important;
  color: #2c2c2c !important;
  border-radius: 2px;
}

/* ──────────────────────────────────────────────
   Card Content
   ────────────────────────────────────────────── */
.card-content {
  padding: 18px 20px 20px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card-name {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: #2c2c2c;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: var(--el-font-size-small);
  color: #2c2c2c;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.meta-item .el-icon {
  font-size: 14px;
}

.heat-score {
  color: #2c2c2c;
  font-weight: 500;
}

.visit-count {
  color: #2c2c2c;
  opacity: 0.7;
}

.card-desc {
  font-size: var(--el-font-size-small);
  color: #2c2c2c;
  opacity: 0.7;
  line-height: 1.6;
  margin: 0 0 auto;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ──────────────────────────────────────────────
   Card Footer: Price + Nav Button
   ────────────────────────────────────────────── */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 2px dashed #2c2c2c;
}

.price-tag {
  display: flex;
  align-items: baseline;
  gap: 1px;
}

.price-currency {
  font-size: var(--el-font-size-small);
  font-weight: 700;
  color: #2c2c2c;
}

.price-value {
  font-size: 22px;
  font-weight: 700;
  color: #2c2c2c;
  line-height: 1;
}

.price-suffix {
  font-size: var(--el-font-size-small);
  color: #2c2c2c;
  margin-left: 2px;
}

.nav-btn {
  --el-button-bg-color: #f5f0e8;
  --el-button-border-color: #2c2c2c;
  --el-button-text-color: #2c2c2c;
  --el-button-hover-bg-color: #e8e2d8;
  --el-button-hover-text-color: #2c2c2c;
  --el-button-hover-border-color: #2c2c2c;
  border: 2px dashed #2c2c2c !important;
  border-radius: 4px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: all 0.12s ease;
}

/* ================================================================
   PAGINATION
   ================================================================ */
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 36px;
  padding: 24px 0 48px;
}

.scenic-pagination :deep(.el-pager li) {
  font-weight: 500;
  border-radius: 4px;
  min-width: 36px;
  transition: all 0.12s ease;
}

.scenic-pagination :deep(.el-pager li.is-active) {
  color: #2c2c2c;
  background: #faf5ed;
  border-radius: 4px;
  font-weight: 600;
  border: 2px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.scenic-pagination :deep(.el-pager li:hover) {
  color: #2c2c2c;
}

.scenic-pagination :deep(.btn-prev),
.scenic-pagination :deep(.btn-next) {
  border-radius: 4px;
  min-width: 36px;
}

.scenic-pagination :deep(.btn-prev:hover),
.scenic-pagination :deep(.btn-next:hover) {
  color: #2c2c2c;
  border-color: #2c2c2c;
}

/* ================================================================
   SKELETON
   ================================================================ */
.skeleton-img {
  width: 100% !important;
  height: 200px !important;
  flex-shrink: 0;
  border-radius: 0 !important;
}

.skeleton-title {
  width: 60%;
  margin-bottom: 14px;
  height: 22px;
}

.skeleton-line {
  width: 80%;
  margin-bottom: 8px;
}

.skeleton-line.short {
  width: 40%;
}

/* ================================================================
   RESPONSIVE
   ================================================================ */
@media (max-width: 768px) {
  .hero-section {
    padding: 24px 16px;
  }

  .hero-title {
    font-size: 24px;
    letter-spacing: 1px;
  }

  .hero-subtitle {
    font-size: 14px;
    margin-bottom: 20px;
  }

  .hero-search {
    max-width: 100%;
  }

  .scenic-grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .scenic-page {
    padding-left: 12px;
    padding-right: 12px;
  }

  .card-image {
    height: 180px;
  }

  .skeleton-img {
    height: 180px !important;
  }

  .card-footer {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .nav-btn {
    width: 100%;
  }

  .price-tag {
    justify-content: center;
  }

  .pagination-wrap {
    padding: 16px 0 32px;
  }
}
</style>
