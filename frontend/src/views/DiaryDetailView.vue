<template>
  <div class="page-container diary-detail" v-loading="loading">
    <!-- Loading skeleton -->
    <template v-if="loading">
      <div class="skeleton-wrap">
        <el-skeleton :rows="1" animated />
        <el-skeleton :rows="2" animated />
        <el-divider />
        <el-skeleton :rows="3" animated />
        <el-divider />
        <el-skeleton :rows="2" animated />
        <el-skeleton :rows="1" animated />
      </div>
    </template>

    <template v-else-if="diary">
      <!-- Detail header with left accent bar -->
      <div class="detail-header">
        <div class="header-accent"></div>
        <div class="header-body">
          <h2 class="detail-title">{{ diary.title }}</h2>
          <div class="detail-meta">
            <span class="meta-rating">
              <el-icon><StarFilled /></el-icon>
              {{ diary.rating }}
            </span>
            <span class="meta-author">by {{ diary.userNickname || '用户' + diary.userId }}</span>
            <span class="meta-date">{{ diary.createdAt || '' }}</span>
          </div>
        </div>
      </div>

      <!-- Content -->
      <div class="detail-content">{{ diary.content || '暂无内容' }}</div>

      <!-- Like -->
      <div class="detail-actions">
        <el-button
          :type="liked ? 'primary' : 'default'"
          @click="toggleLike"
          :loading="likeLoading"
          class="like-btn"
          :class="{ 'is-liked': liked }"
        >
          点赞 ({{ likeCount }})
        </el-button>
      </div>

      <!-- Comments -->
      <el-divider content-position="left" class="section-divider">
        <span class="divider-label">评论 ({{ comments.length }})</span>
      </el-divider>

      <div class="comment-input">
        <el-input
          v-model="commentText"
          placeholder="写下你的评论..."
          :rows="2"
          type="textarea"
          class="comment-textarea"
        />
        <div class="comment-input-actions">
          <el-button
            type="primary"
            size="small"
            round
            :loading="commentLoading"
            @click="postComment"
            class="comment-submit-btn"
          >
            发表评论
          </el-button>
        </div>
      </div>

      <div class="comment-list" v-if="comments.length">
        <div v-for="cm in comments" :key="cm.id" class="comment-item">
          <div class="cm-header">
            <span class="cm-user">{{ cm.userNickname || '用户' + cm.userId }}</span>
            <span class="cm-time">{{ cm.createdAt || '' }}</span>
          </div>
          <p class="cm-content">{{ cm.content }}</p>
        </div>
      </div>
      <el-empty v-else description="暂无评论" :image-size="60" />
    </template>

    <el-empty v-else-if="!loading" description="日记不存在" />

    <!-- Back button -->
    <div class="back-wrap">
      <el-button text @click="$router.back()" class="back-btn">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { diaryApi } from '@/api/modules/diary'
import { StarFilled, ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const diary = ref<any>(null)
const comments = ref<any[]>([])
const loading = ref(false)
const likeCount = ref(0)
const liked = ref(false)
const likeLoading = ref(false)
const commentText = ref('')
const commentLoading = ref(false)

onMounted(async () => {
  const id = route.params.id as string
  loading.value = true
  try {
    const [dRes, cRes] = await Promise.all([
      diaryApi.getById(id),
      diaryApi.getComments(id, { page: 1, size: 50 })
    ])
    if (dRes.data.code === 200) {
      diary.value = dRes.data.data
      likeCount.value = dRes.data.data?.likeCount || 0
    }
    comments.value = (cRes.data?.data?.list || [])
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
})

async function toggleLike() {
  likeLoading.value = true
  try {
    const id = route.params.id as string
    const res = await diaryApi.like(id)
    if (res.data.code === 200) {
      liked.value = !liked.value
      likeCount.value = res.data.data?.likeCount || likeCount.value + (liked.value ? 1 : -1)
    }
  } catch { ElMessage.error('操作失败') }
  finally { likeLoading.value = false }
}

async function postComment() {
  if (!commentText.value.trim()) return
  commentLoading.value = true
  try {
    const id = route.params.id as string
    await diaryApi.comment(id, { content: commentText.value })
    ElMessage.success('评论已发表')
    commentText.value = ''
    // Reload comments
    const res = await diaryApi.getComments(id, { page: 1, size: 50 })
    comments.value = (res.data?.data?.list || [])
  } catch { ElMessage.error('评论失败') }
  finally { commentLoading.value = false }
}
</script>

<style scoped>
.diary-detail {
  max-width: 700px;
  margin: 0 auto;
}

/* ── Skeleton ── */
.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;
}

/* ── Header ── */
.detail-header {
  display: flex;
  gap: 14px;
  margin-bottom: 20px;
}

.header-accent {
  width: 4px;
  min-height: 48px;
  background: #2c2c2c;
  border-radius: 0;
  flex-shrink: 0;
}

.header-body {
  flex: 1;
  min-width: 0;
}

.detail-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0.02em;
  color: #2c2c2c;
  font-family: Georgia, 'Times New Roman', serif;
  padding-bottom: 8px;
  border-bottom: 2px dashed #2c2c2c;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #2c2c2c;
  opacity: 0.7;
}

.meta-rating {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #2c2c2c;
  font-size: 13px;
}

.meta-author {
  color: #2c2c2c;
  opacity: 0.7;
}

.meta-date {
  color: #2c2c2c;
  opacity: 0.5;
}

/* ── Content ── */
.detail-content {
  font-size: 15px;
  line-height: 1.8;
  margin: 16px 0;
  white-space: pre-wrap;
  color: #2c2c2c;
}

/* ── Like Button ── */
.detail-actions {
  margin: 16px 0 8px;
}

.like-btn {
  --el-button-bg-color: #faf5ed;
  --el-button-border-color: #2c2c2c;
  --el-button-text-color: #2c2c2c;
  --el-button-hover-bg-color: #e8e2d8;
  --el-button-hover-border-color: #2c2c2c;
  --el-button-hover-text-color: #2c2c2c;
  --el-button-active-bg-color: #ddd8d0;
  --el-button-active-border-color: #2c2c2c;
  --el-button-active-text-color: #2c2c2c;
  transition: all 0.12s ease;
  border: 2px dashed #2c2c2c;
  border-radius: 4px;
}

.like-btn.is-liked {
  --el-button-bg-color: #2c2c2c;
  --el-button-border-color: #2c2c2c;
  --el-button-text-color: #f5f0e8;
  --el-button-hover-bg-color: #444;
  --el-button-hover-border-color: #444;
  --el-button-hover-text-color: #f5f0e8;
  --el-button-active-bg-color: #2c2c2c;
  --el-button-active-border-color: #2c2c2c;
  --el-button-active-text-color: #f5f0e8;
}

/* ── Divider ── */
.section-divider {
  margin: 20px 0 16px;
  --el-border-color: #2c2c2c;
}

.divider-label {
  font-size: 15px;
  font-weight: 600;
  color: #2c2c2c;
  letter-spacing: 0.01em;
}

/* ── Comment Input ── */
.comment-input {
  margin: 0 0 16px;
}

.comment-textarea {
  --el-input-focus-border-color: #2c2c2c;
  --el-input-hover-border-color: #2c2c2c;
  --el-input-bg-color: #faf5ed;
  --el-input-border-color: #2c2c2c;
  --el-input-text-color: #2c2c2c;
}

.comment-textarea :deep(.el-textarea__inner) {
  border-radius: 4px;
  border: 2px dashed #2c2c2c;
  background: #faf5ed;
  transition: box-shadow 0.12s ease, border-color 0.12s ease;
}

.comment-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.comment-input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.comment-submit-btn {
  --el-button-bg-color: #2c2c2c;
  --el-button-border-color: #2c2c2c;
  --el-button-hover-bg-color: #444;
  --el-button-hover-border-color: #444;
  --el-button-active-bg-color: #2c2c2c;
  --el-button-active-border-color: #2c2c2c;
  border-radius: 4px;
}

/* ── Comment List ── */
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-item {
  padding: 14px 16px;
  background: #faf5ed;
  border-radius: 4px;
  border: 2px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  border-left: 3px solid #2c2c2c;
  transition: box-shadow 0.12s ease;
}

.comment-item:hover {
  box-shadow: 4px 4px 0px rgba(44,44,44,0.3);
}

.cm-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.cm-user {
  font-weight: 600;
  font-size: 13px;
  color: #2c2c2c;
}

.cm-time {
  font-size: 11px;
  color: #2c2c2c;
  opacity: 0.5;
}

.cm-content {
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  color: #2c2c2c;
}

/* ── Back Button ── */
.back-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: flex-start;
}

.back-btn {
  color: #2c2c2c;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 4px 8px;
  transition: color 0.12s ease;
}

.back-btn:hover {
  color: #2c2c2c;
  opacity: 0.7;
  background: transparent;
}
</style>
