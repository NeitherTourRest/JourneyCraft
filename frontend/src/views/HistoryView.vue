<template>
  <div class="page-container history-page">
    <!-- ── Page Title — teal accent underline ── -->
    <div class="page-title-bar">
      <h2 class="page-title">历史记录</h2>
      <el-button
        v-if="items.length > 0"
        plain
        size="small"
        class="clear-btn"
        @click="clearAll"
      >
        <el-icon><Delete /></el-icon>
        清空记录
      </el-button>
    </div>

    <!-- ── Tabs ── -->
    <el-tabs v-model="tab" @tab-change="loadData" class="history-tabs">
      <!-- ════════════════ Browse History ════════════════ -->
      <el-tab-pane name="view">
        <template #label>
          <span class="tab-label">
            <el-icon><Clock /></el-icon>
            浏览历史
          </span>
        </template>

        <!-- Loading skeletons -->
        <div v-if="loading" class="card-list">
          <el-card
            v-for="n in 4"
            :key="'skel-v-' + n"
            class="history-card"
            shadow="never"
          >
            <el-skeleton animated>
              <template #template>
                <div class="skel-row">
                  <el-skeleton-item variant="circle" class="skel-icon" />
                  <div class="skel-info">
                    <el-skeleton-item variant="h3" class="skel-title" />
                    <el-skeleton-item variant="text" class="skel-line short" />
                  </div>
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </div>

        <!-- Date-grouped cards -->
        <div v-else-if="dateGroups.length > 0" class="card-list">
          <template v-for="group in dateGroups" :key="group.label">
            <div class="date-section-header">
              <span class="date-badge">{{ group.label }}</span>
            </div>
            <el-card
              v-for="(item, i) in group.items"
              :key="item.id || 'v-' + group.label + '-' + i"
              class="history-card"
              shadow="never"
              @click="goToView(item)"
            >
              <div class="history-card-body">
                <div
                  class="hc-icon"
                  :style="{ background: getTypeColor(item.type) + '16' }"
                >
                  <el-icon :color="getTypeColor(item.type)" :size="20">
                    <CollectionTag />
                  </el-icon>
                </div>
                <div class="hc-info">
                  <div class="hc-title">
                    {{ item.name || item.title || '浏览记录' }}
                  </div>
                  <div class="hc-meta">
                    <span class="hc-time">
                      {{ formatTime(item.createdAt || item.time) }}
                    </span>
                    <el-tag
                      size="small"
                      effect="plain"
                      class="type-tag"
                      :style="tagStyle(item.type)"
                    >
                      {{ getTypeLabel(item.type) }}
                    </el-tag>
                  </div>
                </div>
                <el-icon class="hc-arrow"><ArrowRight /></el-icon>
              </div>
            </el-card>
          </template>
        </div>

        <!-- Custom empty state -->
        <el-empty v-else>
          <template #image>
            <div class="empty-icon-wrap">
              <span class="empty-hero-emoji">👀</span>
            </div>
          </template>
          <template #description>
            <p class="empty-desc">还没有浏览记录</p>
          </template>
        </el-empty>
      </el-tab-pane>

      <!-- ════════════════ Search History ════════════════ -->
      <el-tab-pane name="search">
        <template #label>
          <span class="tab-label">
            <el-icon><Search /></el-icon>
            搜索历史
          </span>
        </template>

        <!-- Loading skeletons -->
        <div v-if="loading" class="card-list">
          <el-card
            v-for="n in 2"
            :key="'skel-s-' + n"
            class="history-card"
            shadow="never"
          >
            <el-skeleton animated>
              <template #template>
                <div style="padding: 8px 0">
                  <el-skeleton-item
                    variant="text"
                    style="width: 60%; height: 32px"
                  />
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </div>

        <!-- Date-grouped chip tags -->
        <div v-else-if="dateGroups.length > 0" class="search-history">
          <template v-for="group in dateGroups" :key="group.label">
            <div class="date-section-header">
              <span class="date-badge">{{ group.label }}</span>
            </div>
            <div class="search-chips">
              <el-tag
                v-for="(item, i) in group.items"
                :key="item.id || item.query || 's-' + group.label + '-' + i"
                class="search-chip"
                effect="plain"
                @click="searchAgain(item)"
              >
                <el-icon><Search /></el-icon>
                {{ item.query }}
              </el-tag>
            </div>
          </template>
        </div>

        <!-- Custom empty state -->
        <el-empty v-else>
          <template #image>
            <div class="empty-icon-wrap">
              <span class="empty-hero-emoji">🔍</span>
            </div>
          </template>
          <template #description>
            <p class="empty-desc">还没有搜索记录</p>
          </template>
        </el-empty>
      </el-tab-pane>

      <!-- ════════════════ Navigation History ════════════════ -->
      <el-tab-pane name="nav">
        <template #label>
          <span class="tab-label">
            <el-icon><Location /></el-icon>
            导航历史
          </span>
        </template>

        <!-- Loading skeletons -->
        <div v-if="loading" class="card-list">
          <el-card
            v-for="n in 3"
            :key="'skel-n-' + n"
            class="history-card"
            shadow="never"
          >
            <el-skeleton animated>
              <template #template>
                <div class="skel-nav-block">
                  <el-skeleton-item variant="text" class="skel-nav-line" />
                  <el-skeleton-item
                    variant="text"
                    class="skel-nav-line short"
                  />
                  <el-skeleton-item variant="text" class="skel-nav-line" />
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </div>

        <!-- Date-grouped nav cards with route visualization -->
        <div v-else-if="dateGroups.length > 0" class="card-list">
          <template v-for="group in dateGroups" :key="group.label">
            <div class="date-section-header">
              <span class="date-badge">{{ group.label }}</span>
            </div>
            <el-card
              v-for="(item, i) in group.items"
              :key="item.id || 'n-' + group.label + '-' + i"
              class="history-card"
              shadow="never"
              @click="goToNav(item)"
            >
              <div class="history-card-body">
                <div class="hc-icon nav-icon">
                  <el-icon color="var(--el-color-primary)" :size="20">
                    <Location />
                  </el-icon>
                </div>
                <div class="nav-route-viz">
                  <span class="route-dot start"></span>
                  <span class="route-line"></span>
                  <span class="route-dot mid"></span>
                  <span class="route-line"></span>
                  <span class="route-dot end"></span>
                </div>
                <div class="hc-info">
                  <div class="hc-title nav-title">
                    <span class="nav-from">{{
                      item.fromName || item.startName || '起点'
                    }}</span>
                    <span class="nav-arrow-text">→</span>
                    <span class="nav-to">{{
                      item.toName ||
                      item.endName ||
                      item.scenicAreaName ||
                      '终点'
                    }}</span>
                  </div>
                  <div class="hc-meta nav-meta">
                    <span class="hc-time">{{ formatTime(item.createdAt || item.time) }}</span>
                    <span v-if="item.distance" class="nav-stat">
                      {{ formatDistance(item.distance) }}
                    </span>
                    <span v-if="item.duration" class="nav-stat">
                      {{ formatDuration(item.duration) }}
                    </span>
                  </div>
                </div>
                <el-icon class="hc-arrow"><ArrowRight /></el-icon>
              </div>
            </el-card>
          </template>
        </div>

        <!-- Custom empty state -->
        <el-empty v-else>
          <template #image>
            <div class="empty-icon-wrap">
              <span class="empty-hero-emoji">🧭</span>
            </div>
          </template>
          <template #description>
            <p class="empty-desc">还没有导航记录</p>
          </template>
        </el-empty>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  Clock,
  Search,
  Location,
  Delete,
  CollectionTag,
  ArrowRight,
} from '@element-plus/icons-vue'
import { historyApi } from '@/api/modules/history'

const router = useRouter()
const tab = ref('view')
const items = ref<any[]>([])
const loading = ref(false)

// ── Type mapping (matches favorite type constants) ──
const typeMap: Record<number, { label: string; color: string }> = {
  0: { label: '景点', color: '#FF6B35' },
  1: { label: '校园', color: '#2196F3' },
  2: { label: '建筑', color: '#9C27B0' },
  3: { label: '设施', color: '#4CAF50' },
  4: { label: '日记', color: '#E040FB' },
  5: { label: '路线', color: '#00BCD4' },
}

function getTypeColor(type: number): string {
  return typeMap[type]?.color || 'var(--el-text-color-secondary)'
}

function getTypeLabel(type: number): string {
  return typeMap[type]?.label || '其他'
}

function tagStyle(type: number): Record<string, string> {
  const c = getTypeColor(type)
  return {
    '--el-tag-bg-color': c + '16',
    '--el-tag-text-color': c,
    '--el-tag-border-color': c + '30',
    '--el-tag-hover-color': c,
  }
}

// ── Date grouping helpers ──
function getDateGroupKey(dateStr: string): string {
  if (!dateStr) return '更早'
  const date = new Date(dateStr)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const dayOfWeek = today.getDay()
  const weekStart = new Date(
    today.getTime() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1) * 86400000
  )
  if (date >= today) return '今天'
  if (date >= yesterday) return '昨天'
  if (date >= weekStart) return '本周'
  return '更早'
}

function groupByDate(src: any[]): { label: string; items: any[] }[] {
  const map = new Map<string, any[]>()
  for (const item of src) {
    const key = getDateGroupKey(item.createdAt || item.time)
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(item)
  }
  const order = ['今天', '昨天', '本周', '更早']
  const result: { label: string; items: any[] }[] = []
  for (const label of order) {
    if (map.has(label)) {
      result.push({ label, items: map.get(label)! })
    }
  }
  return result
}

const dateGroups = computed(() => groupByDate(items.value))

// ── Formatting helpers ──
function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDistance(meters: number): string {
  if (meters == null) return ''
  return meters >= 1000
    ? `${(meters / 1000).toFixed(1)} km`
    : `${Math.round(meters)} m`
}

function formatDuration(seconds: number): string {
  if (seconds == null) return ''
  const m = Math.floor(seconds / 60)
  if (m < 1) return '不足1分钟'
  if (m < 60) return `${m}分钟`
  const h = Math.floor(m / 60)
  const rm = m % 60
  return rm > 0 ? `${h}小时${rm}分钟` : `${h}小时`
}

// ── Navigation handlers ──
function goToView(item: any) {
  const id = item.targetId ?? item.id
  if (id && (item.type === 0 || item.type === 2)) {
    router.push(`/navigation/${id}`)
  }
}

function searchAgain(item: any) {
  if (item.query) {
    router.push('/scenic?keyword=' + encodeURIComponent(item.query))
  }
}

function goToNav(item: any) {
  const sid = item.scenicAreaId
  if (sid) {
    router.push(`/navigation/${sid}`)
  }
}

// ── Data loading (unchanged logic) ──
async function loadData() {
  loading.value = true
  try {
    let res: any
    if (tab.value === 'view')
      res = await historyApi.getViewHistory({ page: 1, size: 50 })
    else if (tab.value === 'search')
      res = await historyApi.getSearchHistory({ limit: 50 })
    else res = await historyApi.getNavHistory({})
    const data = Array.isArray(res?.data?.data)
      ? res.data.data
      : res?.data?.data?.list || res?.data?.data || []
    items.value = data
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function clearAll() {
  try {
    await historyApi.clear()
    items.value = []
    ElMessage.success('已清空')
  } catch {
    ElMessage.error('清空失败')
  }
}

loadData()
</script>

<style scoped>
/* ═══════════════════════════════════════════════
   Page Layout
   ═══════════════════════════════════════════════ */
.history-page {
  max-width: 700px;
  margin: 0 auto;
}

/* ═══════════════════════════════════════════════
   Page Title — sketch dashed underline
   ═══════════════════════════════════════════════ */
.page-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #2c2c2c;
  margin: 0;
  line-height: 1.3;
  position: relative;
  padding-bottom: 10px;
  display: inline-block;
  font-family: Georgia, 'Times New Roman', serif;
}

.page-title::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 60%;
  height: 2px;
  background: #2c2c2c;
  border-radius: 0;
}

/* ── Clear button ── */
.clear-btn {
  border-radius: 4px;
  font-weight: 500;
  font-size: 13px;
  --el-button-text-color: #e74c3c;
  --el-button-bg-color: #faf5ed;
  --el-button-border-color: #2c2c2c;
  --el-button-hover-text-color: #e74c3c;
  --el-button-hover-bg-color: #e8e2d8;
  --el-button-hover-border-color: #2c2c2c;
  --el-button-active-text-color: #e74c3c;
  --el-button-active-bg-color: #ddd8d0;
  --el-button-active-border-color: #2c2c2c;
  border: 2px dashed #2c2c2c !important;
}

.clear-btn .el-icon {
  margin-right: 3px;
}

/* ═══════════════════════════════════════════════
   Tabs
   ═══════════════════════════════════════════════ */
.history-tabs {
  --el-tabs-header-height: 44px;
}

.history-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 500;
  padding: 0 16px;
  transition: color 0.12s ease;
  color: #2c2c2c;
}

.history-tabs :deep(.el-tabs__item.is-active) {
  font-weight: 600;
  color: #2c2c2c;
}

.history-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  border-radius: 0;
  background-color: #2c2c2c;
}

.history-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: rgba(44,44,44,0.15);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-label .el-icon {
  font-size: 16px;
}

/* ═══════════════════════════════════════════════
   Date Section Headers
   ═══════════════════════════════════════════════ */
.date-section-header {
  display: flex;
  align-items: center;
  margin: 20px 0 10px;
  padding-left: 10px;
  border-left: 3px solid #2c2c2c;
}

.date-section-header:first-of-type {
  margin-top: 0;
}

.date-badge {
  display: inline-block;
  font-size: 16px;
  font-weight: 700;
  color: #2c2c2c;
  line-height: 1.4;
}

/* ═══════════════════════════════════════════════
   Card List Container
   ═══════════════════════════════════════════════ */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ═══════════════════════════════════════════════
   History Card — Sketch Style
   ═══════════════════════════════════════════════ */
.history-card {
  --el-card-padding: 0;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 2px dashed #2c2c2c;
  background: #faf5ed;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  transition: box-shadow 0.12s ease;
}

.history-card:hover {
  box-shadow: 4px 4px 0px rgba(44,44,44,0.3);
}

.history-card-body {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  gap: 14px;
}

/* ── Icon circle — 44px ── */
.hc-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #e8e2d8;
  border: 2px dashed #2c2c2c;
}

.nav-icon {
  background: #e8e2d8;
}

/* ── Nav Route Visualization ── */
.nav-route-viz {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.route-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.route-dot.start {
  background: #2c2c2c;
}

.route-dot.mid {
  background: #2c2c2c;
  opacity: 0.6;
}

.route-dot.end {
  background: #2c2c2c;
}

.route-line {
  width: 14px;
  height: 2px;
  background: #2c2c2c;
  opacity: 0.3;
  border-radius: 0;
  flex-shrink: 0;
}

/* ── Info block ── */
.hc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hc-title {
  font-size: 15px;
  font-weight: 600;
  color: #2c2c2c;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hc-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hc-time {
  font-size: 12px;
  color: #2c2c2c;
  opacity: 0.7;
}

/* ── Type tag ── */
.type-tag {
  font-size: 11px;
  font-weight: 500;
  border-radius: 2px;
  padding: 0 6px;
  line-height: 1.6;
  border: none;
}

/* ── Arrow ── */
.hc-arrow {
  font-size: 16px;
  color: #2c2c2c;
  opacity: 0.4;
  flex-shrink: 0;
  transition: color 0.12s ease, transform 0.12s ease;
}

.history-card:hover .hc-arrow {
  color: #2c2c2c;
  opacity: 0.8;
  transform: translateX(3px);
}

/* ═══════════════════════════════════════════════
   Nav Card Specifics
   ═══════════════════════════════════════════════ */
.nav-title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
}

.nav-from,
.nav-to {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-from {
  max-width: 35%;
  color: #2c2c2c;
}

.nav-to {
  max-width: 40%;
  color: #2c2c2c;
}

.nav-arrow-text {
  flex-shrink: 0;
  color: #2c2c2c;
  font-weight: 700;
  font-size: 14px;
}

.nav-meta {
  gap: 14px;
}

.nav-stat {
  font-size: 12px;
  color: #2c2c2c;
  opacity: 0.7;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.nav-stat::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #2c2c2c;
  opacity: 0.3;
  margin-right: 6px;
  vertical-align: middle;
}

/* ═══════════════════════════════════════════════
   Search History — Chip Tags
   ═══════════════════════════════════════════════ */
.search-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 0;
}

.search-chip {
  --el-tag-bg-color: #faf5ed;
  --el-tag-text-color: #2c2c2c;
  --el-tag-border-color: #2c2c2c;
  --el-tag-hover-color: #2c2c2c;
  border-radius: 4px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  line-height: 2;
  cursor: pointer;
  transition: all 0.12s ease;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 2px dashed #2c2c2c;
}

.search-chip:hover {
  --el-tag-bg-color: #e8e2d8;
  --el-tag-border-color: #2c2c2c;
  --el-tag-text-color: #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.search-chip .el-icon {
  font-size: 14px;
}

/* ═══════════════════════════════════════════════
   Custom Empty States
   ═══════════════════════════════════════════════ */
.empty-icon-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 4px;
}

.empty-hero-emoji {
  font-size: 64px;
  line-height: 1;
  display: inline-block;
  transition: transform 0.12s ease;
}

.empty-hero-emoji:hover {
  transform: scale(1.1) rotate(-5deg);
}

.empty-desc {
  font-size: 15px;
  color: #2c2c2c;
  opacity: 0.7;
  margin: 0;
  font-weight: 500;
}

/* ═══════════════════════════════════════════════
   Skeleton Layouts
   ═══════════════════════════════════════════════ */
.skel-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0;
}

.skel-icon {
  width: 44px !important;
  height: 44px !important;
  flex-shrink: 0;
  border-radius: 50% !important;
}

.skel-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skel-title {
  width: 55%;
  height: 18px;
}

.skel-line {
  width: 80%;
  height: 14px;
}

.skel-line.short {
  width: 40%;
}

.skel-nav-block {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skel-nav-line {
  width: 70%;
  height: 16px;
}

.skel-nav-line.short {
  width: 40%;
}

/* ═══════════════════════════════════════════════
   Responsive — Mobile
   ═══════════════════════════════════════════════ */
@media (max-width: 768px) {
  .page-title-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .clear-btn {
    align-self: flex-end;
  }

  .history-card-body {
    padding: 12px 14px;
    gap: 12px;
  }

  .hc-icon {
    width: 40px;
    height: 40px;
  }

  .nav-route-viz {
    display: none;
  }

  .hc-title {
    font-size: 14px;
  }

  .history-tabs :deep(.el-tabs__item) {
    padding: 0 10px;
    font-size: 13px;
  }

  .search-chip {
    font-size: 12px;
    padding: 0 12px;
    line-height: 1.8;
  }

  .page-title {
    font-size: 24px;
  }

  .empty-hero-emoji {
    font-size: 52px;
  }
}
</style>
