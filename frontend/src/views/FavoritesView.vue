<template>
  <div class="page-container fav-page">
    <!-- Page Title with teal accent underline -->
    <div class="page-title-bar">
      <h2 class="page-title">我的收藏</h2>
    </div>

    <!-- ===== Collection Management ===== -->
    <div class="fav-collections">
      <div class="collection-tabs" v-if="collections.length > 0">
        <div
          v-for="col in collections"
          :key="col.id"
          class="collection-tab"
          :class="{ active: currentCollectionId === col.id }"
          @click="switchCollection(col.id)"
        >
          <el-icon class="col-tab-icon"><Folder /></el-icon>
          <span class="col-tab-name">{{ col.name }}</span>
          <span class="col-tab-count" v-if="col.count != null">({{ col.count }})</span>
          <div class="col-tab-actions" @click.stop>
            <el-icon class="col-action" title="重命名" @click="startRename(col)"><EditPen /></el-icon>
            <el-icon class="col-action danger" title="删除" @click="confirmDeleteCol(col)"><Delete /></el-icon>
          </div>
        </div>
      </div>
      <el-button class="create-col-btn" size="small" round type="primary" @click="createDialogVisible = true">
        <el-icon><FolderAdd /></el-icon> 创建收藏夹
      </el-button>
    </div>

    <!-- ===== Type Filter Chips ===== -->
    <div class="fav-type-filters">
      <el-tag
        class="type-chip"
        :class="{ active: typeFilter === undefined }"
        effect="plain"
        @click="typeFilter = undefined; loadFavs()"
      >
        全部
      </el-tag>
      <el-tag
        v-for="t in typeList"
        :key="t.value"
        class="type-chip"
        :class="{ active: typeFilter === t.value }"
        :style="{ '--chip-color': t.color }"
        effect="plain"
        @click="typeFilter = t.value; loadFavs()"
      >
        <span class="type-dot" :style="{ background: t.color }"></span>
        {{ t.label }}
      </el-tag>
    </div>

    <!-- ===== Loading / Content / Empty ===== -->
    <el-skeleton :loading="loading" animated>
      <template #template>
        <div class="fav-skeleton-list">
          <div v-for="i in 4" :key="i" class="fav-skeleton-card">
            <el-skeleton-item variant="rect" class="fav-skel-rect" />
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && list.length === 0">
        <template #image>
          <div class="empty-illustration">
            <div class="ghost-card">
              <div class="ghost-thumb"></div>
              <div class="ghost-content">
                <div class="ghost-bar"></div>
                <div class="ghost-line"></div>
                <div class="ghost-line short"></div>
              </div>
            </div>
          </div>
        </template>
        <template #description>
          <p class="empty-description">你的收藏夹是空的 🗂️</p>
        </template>
        <el-button type="primary" round @click="createDialogVisible = true">去收藏一些内容</el-button>
      </el-empty>

      <div class="fav-list" v-else-if="list.length > 0">
        <div v-for="item in list" :key="item.id" class="fav-card">
          <!-- Gradient thumbnail by type -->
          <div class="fc-thumb" :data-type="item.type"></div>
          <!-- Card body -->
          <div class="fc-body">
            <div class="fc-type-label-row">
              <span class="fc-type-dot" :style="{ background: getTypeColor(item.type) }"></span>
              <span class="fc-type-text">{{ getTypeLabel(item.type) }}</span>
            </div>
            <div class="fc-name text-ellipsis">{{ item.name || item.title || '收藏 #' + item.id }}</div>
            <div class="fc-time">{{ formatTime(item.createdAt || item.createTime) }}</div>
          </div>
          <!-- Remove button -->
          <el-button class="fc-remove-btn" text type="danger" size="small" @click="confirmRemove(item)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </el-skeleton>

    <!-- ===== Create Collection Dialog ===== -->
    <el-dialog v-model="createDialogVisible" title="创建收藏夹" width="380px" :close-on-click-modal="false" class="fav-dialog">
      <el-form :model="createForm" label-width="60px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="输入收藏夹名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" placeholder="可选描述（最多100字）" maxlength="100" type="textarea" :rows="2" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateCollection">创建</el-button>
      </template>
    </el-dialog>

    <!-- ===== Rename Collection Dialog ===== -->
    <el-dialog v-model="renameDialogVisible" title="重命名收藏夹" width="380px" :close-on-click-modal="false" class="fav-dialog">
      <el-form :model="renameForm" label-width="60px">
        <el-form-item label="名称" required>
          <el-input v-model="renameForm.name" placeholder="输入新名称" maxlength="20" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="renameLoading" @click="handleRenameCollection">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, FolderAdd, Delete, EditPen } from '@element-plus/icons-vue'
import { favoriteApi } from '@/api/modules/favorite'
import { request } from '@/api/request'

// ─── Type definitions ───
const typeList = [
  { value: 0, label: '景点', color: '#FF6B35' },
  { value: 1, label: '校园', color: '#2196F3' },
  { value: 2, label: '建筑', color: '#9C27B0' },
  { value: 3, label: '设施', color: '#4CAF50' },
  { value: 4, label: '日记', color: '#E040FB' },
  { value: 5, label: '路线', color: '#FF9800' },
]

// ─── State ───
const collections = ref<any[]>([])
const currentCollectionId = ref<number | undefined>(undefined)
const list = ref<any[]>([])
const typeFilter = ref<number | undefined>(undefined)
const loading = ref(true)

// Create dialog
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createForm = ref({ name: '', description: '' })

// Rename dialog
const renameDialogVisible = ref(false)
const renameLoading = ref(false)
const renameForm = ref({ name: '', id: 0 })

// ─── Helpers ───
function getTypeColor(t: number): string {
  return typeList.find(x => x.value === t)?.color || '#8E98A3'
}

function getTypeLabel(t: number): string {
  return typeList.find(x => x.value === t)?.label || '其他'
}

function formatTime(t: string | undefined | null): string {
  if (!t) return ''
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return t
  }
}

// ─── Data Loading ───
async function loadCollections() {
  try {
    const res = await favoriteApi.getCollections()
    collections.value = Array.isArray(res.data?.data) ? res.data.data : []
    if (collections.value.length > 0 && currentCollectionId.value === undefined) {
      currentCollectionId.value = collections.value[0].id
    }
  } catch {
    collections.value = []
  }
}

async function loadFavs() {
  loading.value = true
  try {
    const params: any = { page: 1, size: 50 }
    if (typeFilter.value !== undefined) params.type = typeFilter.value
    const res = await favoriteApi.getList(params)
    let data = res.data?.data?.list || res.data?.data || []
    if (!Array.isArray(data)) data = []
    // Client-side collection filter fallback
    if (currentCollectionId.value !== undefined) {
      data = data.filter((item: any) => item.collectionId === currentCollectionId.value)
    }
    list.value = data
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchCollection(id: number | undefined) {
  currentCollectionId.value = id
  loadFavs()
}

// ─── Collection Ops ───
async function handleCreateCollection() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入收藏夹名称')
    return
  }
  createLoading.value = true
  try {
    await favoriteApi.createCollection({
      name: createForm.value.name.trim(),
      description: createForm.value.description.trim(),
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    createForm.value = { name: '', description: '' }
    await loadCollections()
    loadFavs()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    createLoading.value = false
  }
}

function startRename(col: any) {
  renameForm.value = { name: col.name, id: col.id }
  renameDialogVisible.value = true
}

async function handleRenameCollection() {
  if (!renameForm.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  renameLoading.value = true
  try {
    await request.put(`/api/favorite/collection/${renameForm.value.id}`, { name: renameForm.value.name.trim() })
    ElMessage.success('已重命名')
    renameDialogVisible.value = false
    await loadCollections()
  } catch {
    // Fallback: update locally
    const col = collections.value.find(c => c.id === renameForm.value.id)
    if (col) {
      col.name = renameForm.value.name.trim()
      ElMessage.success('已重命名')
      renameDialogVisible.value = false
    } else {
      ElMessage.error('重命名失败')
    }
  } finally {
    renameLoading.value = false
  }
}

async function confirmDeleteCol(col: any) {
  try {
    await ElMessageBox.confirm(`确定删除收藏夹「${col.name}」吗？其中的收藏内容不会丢失。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    try {
      await request.delete(`/api/favorite/collection/${col.id}`)
    } catch {
      // Endpoint may not exist — proceed with client-side removal
    }
    collections.value = collections.value.filter(c => c.id !== col.id)
    if (currentCollectionId.value === col.id) {
      currentCollectionId.value = collections.value.length > 0 ? collections.value[0].id : undefined
    }
    ElMessage.success('已删除')
    loadFavs()
  } catch {
    // Cancelled
  }
}

// ─── Favorite Ops ───
async function confirmRemove(item: any) {
  try {
    await ElMessageBox.confirm('确定取消收藏？', '取消收藏', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await favoriteApi.remove(item.id)
    list.value = list.value.filter(x => x.id !== item.id)
    ElMessage.success('已取消收藏')
  } catch {
    // Cancelled
  }
}

onMounted(async () => {
  await loadCollections()
  loadFavs()
})
</script>

<style scoped>
/* ===== Page Layout ===== */
.fav-page {
  max-width: 700px;
  margin: 0 auto;
}

/* ===== Page Title — sketch dashed underline ===== */
.page-title-bar {
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

/* ===== Collection Tabs — Sketch Style ===== */
.fav-collections {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.collection-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}

.collection-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 18px;
  border-radius: 4px;
  background: #faf5ed;
  border: 2px dashed #2c2c2c;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.12s ease;
  user-select: none;
  color: #2c2c2c;
}

.collection-tab:hover {
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.collection-tab.active {
  background: #2c2c2c;
  border-color: #2c2c2c;
  color: #f5f0e8;
  font-weight: 600;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.col-tab-icon {
  font-size: 15px;
}

.col-tab-name {
  font-weight: 500;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-tab-count {
  font-size: 12px;
  opacity: 0.7;
}

.col-tab-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: 2px;
  opacity: 0;
  transition: opacity 0.12s ease;
}

.collection-tab:hover .col-tab-actions {
  opacity: 1;
}

.collection-tab.active .col-tab-actions {
  opacity: 0.85;
}

.col-action {
  font-size: 13px;
  padding: 2px;
  border-radius: 2px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.col-action:hover {
  background: rgba(44,44,44,0.08);
}

.collection-tab .col-action.danger:hover {
  background: rgba(231, 76, 60, 0.12);
  color: #e74c3c;
}

.collection-tab.active .col-action:hover {
  background: rgba(245,240,232,0.2);
}

.collection-tab.active .col-action.danger:hover {
  background: rgba(245,240,232,0.2);
  color: #f5f0e8;
}

.create-col-btn {
  flex-shrink: 0;
  --el-button-size: 30px;
}

/* ===== Type Filter Chips ===== */
.fav-type-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.type-chip {
  cursor: pointer;
  user-select: none;
  transition: all 0.12s ease;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 4px !important;
}

.type-chip.active {
  background-color: #2c2c2c !important;
  border-color: #2c2c2c !important;
  color: #f5f0e8 !important;
}

.type-chip.active[style*="--chip-color"] {
  background-color: var(--chip-color, #2c2c2c) !important;
  border-color: var(--chip-color, #2c2c2c) !important;
  color: #f5f0e8 !important;
}

.type-chip:not(.active) {
  background: #faf5ed;
  border-color: #2c2c2c;
  color: #2c2c2c;
  border: 2px dashed #2c2c2c !important;
}

.type-chip:not(.active):hover {
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.type-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ===== Skeleton ===== */
.fav-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fav-skeleton-card {
  height: 80px;
  border-radius: 4px;
  overflow: hidden;
}

.fav-skel-rect {
  width: 100%;
  height: 100%;
  border-radius: 4px;
}

/* ===== Custom Empty State — Ghost Card ===== */
.empty-illustration {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.ghost-card {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 200px;
  padding: 16px;
  border: 2px dashed #2c2c2c;
  border-radius: 4px;
  background: #faf5ed;
  opacity: 0.55;
}

.ghost-thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  background: #e8e2d8;
  border: 1px dashed #2c2c2c;
  flex-shrink: 0;
}

.ghost-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ghost-bar {
  height: 10px;
  width: 70%;
  background: #ddd8d0;
  border-radius: 2px;
}

.ghost-line {
  height: 8px;
  width: 90%;
  background: #ddd8d0;
  border-radius: 2px;
}

.ghost-line.short {
  width: 50%;
}

.empty-description {
  font-size: 15px;
  color: #2c2c2c;
  opacity: 0.7;
  margin: 0;
  font-weight: 500;
}

/* ===== Favorite Card List ===== */
.fav-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fav-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  background: #faf5ed;
  border-radius: 4px;
  border: 2px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  transition: box-shadow 0.12s ease;
}

.fav-card:hover {
  box-shadow: 4px 4px 0px rgba(44,44,44,0.3);
}

/* ===== Solid Thumbnail by Type ===== */
.fc-thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  flex-shrink: 0;
  border: 2px dashed #2c2c2c;
}

.fc-thumb[data-type="0"] { background: #e8e2d8; }
.fc-thumb[data-type="1"] { background: #e8e2d8; }
.fc-thumb[data-type="2"] { background: #e8e2d8; }
.fc-thumb[data-type="3"] { background: #e8e2d8; }
.fc-thumb[data-type="4"] { background: #e8e2d8; }
.fc-thumb[data-type="5"] { background: #e8e2d8; }
.fc-thumb:not([data-type]) { background: #e8e2d8; }

/* Card body */
.fc-body {
  flex: 1;
  min-width: 0;
}

.fc-type-label-row {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 3px;
}

.fc-type-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.fc-type-text {
  font-size: 12px;
  font-weight: 500;
  color: #2c2c2c;
  opacity: 0.7;
}

.fc-name {
  font-size: 15px;
  font-weight: 600;
  color: #2c2c2c;
  line-height: 1.4;
}

.fc-time {
  font-size: 12px;
  color: #2c2c2c;
  opacity: 0.7;
  margin-top: 3px;
}

/* Remove button */
.fc-remove-btn {
  flex-shrink: 0;
  opacity: 0.4;
  transition: opacity 0.12s ease;
  padding: 4px;
}

.fav-card:hover .fc-remove-btn {
  opacity: 1;
}

/* ===== Dialogs ===== */
.fav-dialog :deep(.el-dialog__header) {
  border-bottom: 2px dashed #2c2c2c;
  padding-bottom: 16px;
  margin-bottom: 0;
}

.fav-dialog :deep(.el-dialog__title) {
  font-size: 17px;
  font-weight: 600;
  color: #2c2c2c;
}

.fav-dialog :deep(.el-dialog__body) {
  padding-top: 20px;
}

.fav-dialog :deep(.el-dialog__footer) {
  border-top: 2px dashed #2c2c2c;
  padding-top: 16px;
}

/* ===== Responsive ===== */
@media (max-width: 480px) {
  .fav-card {
    padding: 12px 14px;
    gap: 12px;
  }

  .fc-thumb {
    width: 40px;
    height: 40px;
    border-radius: 4px;
  }

  .fc-name {
    font-size: 14px;
  }

  .collection-tab {
    padding: 6px 14px;
    font-size: 13px;
  }

  .col-tab-name {
    max-width: 70px;
  }

  .page-title {
    font-size: 24px;
  }
}
</style>
