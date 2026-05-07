<template>
  <div class="page-container diary-page">
    <!-- Page Header -->
    <div class="diary-header">
      <h2 class="page-title">旅行日记</h2>
    </div>

    <div class="diary-grid">
      <!-- Skeleton loading cards -->
      <template v-if="loading">
        <el-card v-for="n in 3" :key="'skeleton-' + n" class="diary-card is-skeleton" shadow="never">
          <el-skeleton animated>
            <template #template>
              <div class="diary-card-inner">
                <div class="mood-accent-bar" style="background: var(--el-fill-color)"></div>
                <div class="card-body">
                  <div class="card-top-row">
                    <el-skeleton-item variant="circle" style="width: 32px; height: 32px; flex-shrink: 0;" />
                    <el-skeleton-item variant="h3" style="width: 55%; height: 20px;" />
                    <el-skeleton-item variant="text" style="width: 60px; height: 24px; border-radius: 20px;" />
                  </div>
                  <el-skeleton-item variant="text" style="width: 85%; margin: 10px 0 8px;" />
                  <el-skeleton-item variant="text" style="width: 35%; margin-bottom: 14px;" />
                  <div class="skeleton-meta-row">
                    <el-skeleton-item variant="text" style="width: 50px;" />
                    <el-skeleton-item variant="text" style="width: 50px;" />
                    <el-skeleton-item variant="text" style="width: 70px; margin-left: auto;" />
                  </div>
                </div>
              </div>
            </template>
          </el-skeleton>
        </el-card>
      </template>

      <!-- Custom Empty State -->
      <div v-else-if="list.length === 0" class="custom-empty">
        <div class="notebook-illustration">
          <div class="notebook-cover"></div>
          <div class="notebook-pages">
            <div class="notebook-line"></div>
            <div class="notebook-line"></div>
            <div class="notebook-line short"></div>
          </div>
        </div>
        <p class="empty-text">写下你的第一段旅程记忆 ✨</p>
      </div>

      <!-- Diary cards -->
      <el-card
        v-for="item in list"
        :key="item.id"
        class="diary-card"
        shadow="hover"
        @click="goDetail(item.id)"
      >
        <div class="diary-card-inner">
          <div
            class="mood-accent-bar"
            :style="{ background: moodMap[item.mood]?.color || 'transparent' }"
          ></div>
          <div class="card-body">
            <div class="card-top-row">
              <span class="mood-emoji-large">{{ moodMap[item.mood]?.icon || '' }}</span>
              <h3 class="card-title">{{ item.title }}</h3>
              <span
                v-if="item.mood && moodMap[item.mood]"
                class="mood-tag"
                :style="{ background: moodMap[item.mood].bg, color: moodMap[item.mood].color }"
              >
                {{ moodMap[item.mood].icon }} {{ moodMap[item.mood].label }}
              </span>
            </div>
            <p class="card-summary">{{ item.summary || '暂无摘要' }}</p>
            <div class="card-tags" v-if="item.tags && item.tags.length">
              <el-tag
                v-for="t in item.tags"
                :key="t"
                size="small"
                class="diary-tag"
              >{{ t }}</el-tag>
            </div>
            <div class="card-meta">
              <span class="meta-item">
                <el-icon class="meta-icon"><Star /></el-icon>
                <span class="meta-value">{{ item.rating }}</span>
              </span>
              <span class="meta-item">
                <el-icon class="meta-icon"><Collection /></el-icon>
                <span class="meta-value">{{ item.likeCount || 0 }}</span>
              </span>
              <span class="meta-item">
                <el-icon class="meta-icon"><ChatDotSquare /></el-icon>
                <span class="meta-value">{{ item.commentCount || 0 }}</span>
              </span>
              <span class="meta-item meta-user">{{ item.userNickname || '用户'+item.userId }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="pagination-wrap" v-if="total > size">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadList"
        background
      />
    </div>

    <!-- FAB: 写日记 -->
    <el-button
      class="diary-fab"
      :icon="EditPen"
      @click="openCreate"
      circle
    />

    <!-- Create Dialog -->
    <el-dialog
      v-model="showCreate"
      title="写日记"
      width="560px"
      destroy-on-close
      class="create-dialog"
    >
      <el-form :model="form" label-width="70px" class="diary-form">
        <el-form-item label="标题">
          <template #label>
            <el-icon class="form-label-icon"><EditPen /></el-icon>
            <span>标题</span>
          </template>
          <el-input v-model="form.title" placeholder="给旅行取个名字吧" />
        </el-form-item>
        <el-form-item label="内容">
          <template #label>
            <el-icon class="form-label-icon"><Notebook /></el-icon>
            <span>内容</span>
          </template>
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="记录旅途中的点点滴滴..." />
        </el-form-item>
        <el-form-item label="标签">
          <template #label>
            <el-icon class="form-label-icon"><CollectionTag /></el-icon>
            <span>标签</span>
          </template>
          <el-input v-model="tagInput" placeholder="用逗号分隔" @blur="parseTags" />
        </el-form-item>
        <el-form-item label="评分">
          <template #label>
            <el-icon class="form-label-icon"><Star /></el-icon>
            <span>评分</span>
          </template>
          <el-rate v-model="form.rating" class="diary-rate" />
        </el-form-item>
        <el-form-item label="心情">
          <template #label>
            <span>心情</span>
          </template>
          <el-select v-model="form.mood" placeholder="选择心情" class="mood-select">
            <el-option
              v-for="m in moods"
              :key="m"
              :value="m"
            >
              <span class="mood-option-item">
                <span
                  class="mood-dot"
                  :style="{ background: moodMap[m]?.color || 'var(--el-text-color-disabled)' }"
                />
                {{ moodMap[m]?.icon }} {{ moodMap[m]?.label || m }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="天气">
          <template #label>
            <el-icon class="form-label-icon"><Sunny /></el-icon>
            <span>天气</span>
          </template>
          <el-input v-model="form.weather" placeholder="晴 / 阴 / 雨 / 雪 ..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" round :loading="createLoading" @click="submitDiary">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { diaryApi } from '@/api/modules/diary'
import {
  EditPen, Notebook, CollectionTag,
  Star, Collection, ChatDotSquare, Sunny,
} from '@element-plus/icons-vue'

const router = useRouter()
const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const showCreate = ref(false)
const createLoading = ref(false)
const tagInput = ref('')
const form = reactive({ title: '', content: '', rating: 3, mood: '', weather: '', tags: [] as string[] })
const moods = ['happy', 'neutral', 'sad', 'excited', 'tired']

const moodMap: Record<string, { bg: string; color: string; icon: string; label: string }> = {
  happy:   { bg: 'var(--el-color-success-light-3)', color: 'var(--el-color-success)',        icon: '😊', label: '开心' },
  neutral: { bg: 'var(--el-fill-color-light)',      color: 'var(--el-text-color-secondary)',  icon: '😐', label: '平静' },
  sad:     { bg: '#F3E5F5',                         color: '#9C27B0',                         icon: '😢', label: '难过' },
  excited: { bg: 'var(--el-color-primary-light-5)',  color: 'var(--el-color-primary-dark-2)',  icon: '😆', label: '兴奋' },
  tired:   { bg: 'var(--el-color-warning-light-3)',  color: 'var(--el-color-warning)',         icon: '😴', label: '疲惫' },
}

function parseTags() { form.tags = tagInput.value.split(/[,，]/).map(s => s.trim()).filter(Boolean) }

onMounted(() => loadList())
async function loadList(p?: number) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await diaryApi.getList({ page: page.value, size: size.value })
    if (res.data.code === 200) {
      list.value = res.data.data?.list || (Array.isArray(res.data.data) ? res.data.data : [])
      total.value = res.data.data?.total || 0
    }
  } catch { list.value = [] }
  finally { loading.value = false }
}

function openCreate() { showCreate.value = true; form.title = ''; form.content = ''; form.tags = []; form.rating = 3; form.mood = ''; form.weather = ''; tagInput.value = '' }
async function submitDiary() {
  if (!form.title) { ElMessage.warning('请填写标题'); return }
  createLoading.value = true
  try {
    await diaryApi.create({
      title: form.title, content: form.content, scenicAreaId: [],
      tags: form.tags, rating: form.rating, status: 1, mood: form.mood, weather: form.weather, images: []
    })
    ElMessage.success('发布成功')
    showCreate.value = false
    page.value = 1
    loadList()
  } catch { ElMessage.error('发布失败') }
  finally { createLoading.value = false }
}

function goDetail(id: string) { router.push('/diary/' + id) }
</script>

<style scoped>
/* =============================================
   Page Layout
   ============================================= */
.diary-page {
  max-width: 800px;
  margin: 0 auto;
  position: relative;
}

.diary-header {
  margin-bottom: 24px;
}

/* =============================================
   Page Title — sketch dashed underline
   ============================================= */
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

/* =============================================
   Grid
   ============================================= */
.diary-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

/* =============================================
   Skeleton Card
   ============================================= */
.diary-card.is-skeleton {
  cursor: default;
  pointer-events: none;
}

.skeleton-meta-row {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 8px;
}

/* =============================================
   Diary Card — Sketch Style
   ============================================= */
.diary-card {
  --el-card-padding: 0;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 2px dashed #2c2c2c;
  background: #faf5ed;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  transition: box-shadow 0.15s ease;
}

.diary-card:hover {
  box-shadow: 4px 4px 0px rgba(44,44,44,0.3);
}

/* =============================================
   Card Inner — flex with accent bar
   ============================================= */
.diary-card-inner {
  display: flex;
  align-items: stretch;
  min-height: 100%;
}

.mood-accent-bar {
  width: 4px;
  flex-shrink: 0;
  border-radius: 0;
}

/* =============================================
   Card Body
   ============================================= */
.card-body {
  flex: 1;
  padding: 16px 18px 14px;
}

/* -- Top row: emoji + title + mood tag -- */
.card-top-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.mood-emoji-large {
  font-size: 32px;
  line-height: 1;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
}

.card-title {
  margin: 0;
  font-size: var(--el-font-size-medium, 16px);
  font-weight: 600;
  color: #2c2c2c;
  line-height: 1.5;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* -- Mood Tag -- */
.mood-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: var(--el-font-size-extra-small, 11px);
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
  line-height: 1.6;
  letter-spacing: 0.3px;
}

/* -- Summary -- */
.card-summary {
  font-size: var(--el-font-size-small, 13px);
  line-height: 1.6;
  color: #2c2c2c;
  opacity: 0.7;
  margin: 0 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* -- Tags -- */
.card-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.diary-tag {
  --el-tag-bg-color: #f5f0e8;
  --el-tag-border-color: #2c2c2c;
  --el-tag-text-color: #2c2c2c;
  --el-tag-hover-color: #2c2c2c;
  border-radius: 2px;
}

/* -- Meta Area -- */
.card-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: var(--el-font-size-extra-small, 12px);
  color: #2c2c2c;
  opacity: 0.7;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-icon {
  font-size: 13px;
  color: #2c2c2c;
  transition: color 0.12s ease;
}

.diary-card:hover .meta-icon {
  color: #2c2c2c;
}

.meta-value {
  font-variant-numeric: tabular-nums;
}

.meta-user {
  margin-left: auto;
  color: #2c2c2c;
  font-size: var(--el-font-size-extra-small, 12px);
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* =============================================
   Custom Empty State — Notebook Illustration
   ============================================= */
.custom-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.notebook-illustration {
  position: relative;
  width: 88px;
  height: 108px;
  margin-bottom: 20px;
}

.notebook-cover {
  position: absolute;
  inset: 0;
  background: #e8e2d8;
  border-radius: 4px;
  border: 2px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  transform: rotate(-4deg);
  transition: transform 0.15s ease;
}

.custom-empty:hover .notebook-cover {
  transform: rotate(-2deg) translateY(-2px);
}

.notebook-pages {
  position: absolute;
  inset: 4px 3px 3px 4px;
  background: #faf5ed;
  border-radius: 4px;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px dashed #2c2c2c;
}

.notebook-line {
  height: 5px;
  background: #2c2c2c;
  opacity: 0.15;
  border-radius: 0;
}

.notebook-line.short {
  width: 55%;
}

.empty-text {
  font-size: 15px;
  color: #2c2c2c;
  margin: 0;
  font-weight: 500;
  letter-spacing: 0.5px;
}

/* =============================================
   Pagination
   ============================================= */
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-bottom: 80px;
}

/* =============================================
   FAB — Floating Action Button (Sketch Style)
   ============================================= */
.diary-fab {
  position: fixed;
  bottom: 32px;
  right: 32px;
  width: 56px !important;
  height: 56px !important;
  padding: 0 !important;
  background: #2c2c2c !important;
  border: 2px dashed #f5f0e8 !important;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3) !important;
  z-index: 100;
  font-size: 24px !important;
  transition: box-shadow 0.12s ease !important;
  color: #f5f0e8 !important;
}

.diary-fab:hover {
  background: #444 !important;
  box-shadow: 4px 4px 0px rgba(44,44,44,0.3) !important;
}

.diary-fab:active {
  box-shadow: 1px 1px 0px rgba(44,44,44,0.3) !important;
}

/* =============================================
   Create Dialog — Sketch Style
   ============================================= */
.create-dialog {
  --el-dialog-border-radius: 4px;
}

.create-dialog :deep(.el-dialog__header) {
  background: #f5f0e8;
  border-bottom: 2px dashed #2c2c2c;
  position: relative;
  z-index: 1;
  padding-bottom: calc(var(--el-dialog-padding-primary, 20px) - 4px);
  margin-bottom: 0;
}

.create-dialog :deep(.el-dialog__header)::before {
  display: none;
}

.create-dialog :deep(.el-dialog__title) {
  font-size: var(--el-font-size-large, 18px);
  font-weight: 600;
  color: #2c2c2c;
  position: relative;
  z-index: 1;
}

.create-dialog :deep(.el-dialog__body) {
  padding-top: var(--el-dialog-padding-primary, 20px);
  background: #faf5ed;
}

/* -- Form -- */
.diary-form :deep(.el-form-item__label) {
  display: inline-flex;
  align-items: center;
  color: #2c2c2c;
  font-weight: 500;
}

.form-label-icon {
  margin-right: 4px;
  color: #2c2c2c;
  font-size: var(--el-font-size-base, 14px);
  vertical-align: middle;
}

/* -- Rating stars override -- */
.diary-form :deep(.diary-rate) {
  --el-rate-fill-color: #2c2c2c;
  --el-rate-void-color: rgba(44,44,44,0.15);
  height: 28px;
}

.diary-form :deep(.diary-rate .el-rate__item) {
  font-size: 20px;
}

/* -- Mood Select -- */
.mood-select {
  width: 100%;
}

.mood-option-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--el-font-size-base, 14px);
}

.mood-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* -- Dialog Footer -- */
.create-dialog :deep(.el-dialog__footer) {
  border-top: 2px dashed #2c2c2c;
  padding-top: var(--el-dialog-padding-primary, 20px);
  background: #faf5ed;
}

/* =============================================
   Responsive
   ============================================= */
@media (max-width: 480px) {
  .diary-fab {
    bottom: 24px;
    right: 24px;
    width: 50px !important;
    height: 50px !important;
    font-size: 20px !important;
    box-shadow: 2px 2px 0px rgba(44,44,44,0.3) !important;
  }

  .card-body {
    padding: 14px 14px 12px;
  }

  .mood-emoji-large {
    font-size: 28px;
    width: 32px;
    height: 32px;
  }

  .page-title {
    font-size: 24px;
  }

  .notebook-illustration {
    width: 72px;
    height: 88px;
  }
}
</style>
