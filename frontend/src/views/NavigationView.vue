<script setup lang="ts">
/**
 * NavigationView — Core navigation flow for JourneyCraft.
 * Integrates: node loading, route planning (single + multi-target),
 * polyline drawing (WGS→GCJ conversion), and route info panel.
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Location, Close, RefreshRight } from '@element-plus/icons-vue'
import { useNavigation } from '@/composables/useNavigation'
import { request } from '@/api/request'
import { wgs84ToGcj02, pathNodesToGcj02 } from '@/utils/coord'
import AmapContainer from '@/components/map/AmapContainer.vue'
import type { PathNode } from '@/types/navigation'

// ──────────────────────────────────────────────
// Navigation composable
// ──────────────────────────────────────────────
const nav = useNavigation()

// ──────────────────────────────────────────────
// Map
// ──────────────────────────────────────────────
const mapRef = ref<InstanceType<typeof AmapContainer> | null>(null)
const mapReady = ref(false)
const mapCenter = ref<[number, number]>([116.397, 39.916])

function onMapReady() {
  mapReady.value = true
  // Re-mark nodes if already loaded before map was ready
  if (nodes.value.length > 0) markNodesOnMap()
}

// ──────────────────────────────────────────────
// Scenic ID input & node loading
// ──────────────────────────────────────────────
const scenicIdInput = ref<number | null>(null)
const nodes = ref<PathNode[]>([])
const nodesLoading = ref(false)

async function loadNodes() {
  if (!scenicIdInput.value) {
    ElMessage.warning('请输入景区 ID')
    return
  }
  nodesLoading.value = true
  try {
    const res = await request.get<any[]>('/api/navigation/nodes/scenic/' + scenicIdInput.value)
    const data = (res.data as any).data
    nodes.value = (Array.isArray(data) ? data : []) as PathNode[]
    nav.scenicAreaId.value = scenicIdInput.value

    // Center map on first node's GCJ-02 coords
    if (nodes.value.length > 0) {
      const first = nodes.value[0]
      const [lng, lat] = wgs84ToGcj02(first.longitude, first.latitude)
      mapCenter.value = [lng, lat]
    }

    if (mapReady.value) markNodesOnMap()
    ElMessage.success(`已加载 ${nodes.value.length} 个路网节点`)
  } catch (err: any) {
    ElMessage.error('加载节点失败：' + (err?.message || '未知错误'))
  } finally {
    nodesLoading.value = false
  }
}

// ──────────────────────────────────────────────
// Node markers on map
// ──────────────────────────────────────────────
function markNodesOnMap() {
  if (!mapRef.value) return
  mapRef.value.clearOverlays()

  for (const node of nodes.value) {
    const [lng, lat] = wgs84ToGcj02(node.longitude, node.latitude)
    const isStart = node.nodeId === nav.startNodeId.value
    const isEnd = nav.endNodeIds.value.includes(node.nodeId)

    let color = '#909399'
    if (isStart) color = '#67C23A'
    else if (isEnd) color = '#FF6B35'

    mapRef.value.addTextMarker(lng, lat, node.name, color)
  }
}

// Watch mapReady — paint nodes if they arrive before the map
watch(mapReady, (ready) => {
  if (ready && nodes.value.length > 0) markNodesOnMap()
})

// ──────────────────────────────────────────────
// Node search filter
// ──────────────────────────────────────────────
const nodeSearch = ref('')

const filteredNodes = computed(() => {
  if (!nodeSearch.value) return nodes.value
  const kw = nodeSearch.value.toLowerCase()
  return nodes.value.filter((n) => n.name.toLowerCase().includes(kw))
})

// ──────────────────────────────────────────────
// Node selection helpers
// ──────────────────────────────────────────────
function setAsStart(node: PathNode) {
  nav.setStartNode(node.nodeId)
  markNodesOnMap()
}

function addAsEnd(node: PathNode) {
  if (!isMultiTarget.value) {
    nav.endNodeIds.value = [node.nodeId]
  } else {
    if (!nav.endNodeIds.value.includes(node.nodeId)) {
      nav.addEndNode(node.nodeId)
    }
  }
  markNodesOnMap()
}

function removeEndNode(nodeId: number) {
  nav.removeEndNode(nodeId)
  markNodesOnMap()
}

// ──────────────────────────────────────────────
// Multi-target mode
// ──────────────────────────────────────────────
const isMultiTarget = ref(false)

// ──────────────────────────────────────────────
// Route planning
// ──────────────────────────────────────────────
const segmentColors = ['#FF6B35', '#3498DB', '#2ECC71', '#F39C12', '#9B59B6']

async function handlePlanRoute() {
  if (!nav.scenicAreaId.value) {
    ElMessage.warning('请先加载景区路网')
    return
  }
  if (!nav.startNodeId.value) {
    ElMessage.warning('请先设置起点')
    return
  }
  if (nav.endNodeIds.value.length === 0) {
    ElMessage.warning('请设置至少一个终点')
    return
  }

  if (isMultiTarget.value) {
    if (nav.endNodeIds.value.length < 2) {
      ElMessage.warning('多目标模式需要至少 2 个终点')
      return
    }
    await nav.planMultiRoute()
    if (nav.multiRoute.value) drawMultiRoute()
  } else {
    await nav.planRoute()
    if (nav.currentRoute.value) drawSingleRoute()
  }
}

function drawSingleRoute() {
  if (!mapRef.value || !nav.routePath.value) return
  mapRef.value.clearOverlays()
  markNodesOnMap()
  mapRef.value.drawPolyline(nav.routePath.value, {
    strokeColor: '#FF6B35',
    strokeWeight: 6,
    showDir: true,
  })
  mapRef.value.setFitView()
}

function drawMultiRoute() {
  if (!mapRef.value || !nav.multiRoute.value) return
  mapRef.value.clearOverlays()
  markNodesOnMap()

  const segments = nav.multiRoute.value.segments
  for (let i = 0; i < segments.length; i++) {
    const path = pathNodesToGcj02(segments[i].nodes)
    mapRef.value.drawPolyline(path, {
      strokeColor: segmentColors[i % segmentColors.length],
      strokeWeight: 6,
      showDir: true,
    })
  }
  mapRef.value.setFitView()
}

// ──────────────────────────────────────────────
// Clear / Reset
// ──────────────────────────────────────────────
function clearRoute() {
  mapRef.value?.clearOverlays()
  markNodesOnMap()
  nav.currentRoute.value = null
  nav.multiRoute.value = null
  ElMessage.success('路线已清除')
}

function resetAll() {
  nav.reset()
  nodes.value = []
  scenicIdInput.value = null
  mapRef.value?.clearOverlays()
  ElMessage.success('已重置全部')
}

// ──────────────────────────────────────────────
// Display helpers
// ──────────────────────────────────────────────
const startNodeName = computed(() => {
  if (!nav.startNodeId.value) return ''
  return nodes.value.find((n) => n.nodeId === nav.startNodeId.value)?.name || `节点 #${nav.startNodeId.value}`
})

const endNodeNames = computed(() =>
  nav.endNodeIds.value.map(
    (id) => nodes.value.find((n) => n.nodeId === id)?.name || `节点 #${id}`,
  ),
)

const isrouteReady = computed(() => !!(nav.currentRoute.value || nav.multiRoute.value))

const multiRouteDistance = computed(() => {
  if (!nav.multiRoute.value) return null
  return (nav.multiRoute.value.totalDistance / 1000).toFixed(1) + ' km'
})

const multiRouteTime = computed(() => {
  if (!nav.multiRoute.value) return null
  return Math.floor(nav.multiRoute.value.totalTime / 60) + ' min'
})

const routeSegmentsCount = computed(() => nav.multiRoute.value?.segments?.length ?? 0)
</script>

<template>
  <div class="nav-page">
    <!-- ════ LEFT PANEL ════ -->
    <aside class="nav-panel">
      <!------ Header ------>
      <div class="panel-header">
        <h2 class="panel-title">路线导航</h2>
        <p class="panel-desc">加载景区路网，规划游览路径</p>
      </div>

      <!------ Scenic ID + Load ------>
      <div class="panel-section">
        <label class="section-label">景区 ID</label>
        <div class="load-row">
          <el-input-number
            v-model="scenicIdInput"
            :min="1"
            :controls="false"
            placeholder="输入景区 ID"
            class="scenic-input"
            size="default"
          />
          <el-button
            type="primary"
            :loading="nodesLoading"
            :icon="Search"
            @click="loadNodes"
          >
            加载路网
          </el-button>
        </div>
      </div>

      <!------ Strategy & Transport ------>
      <div class="panel-section">
        <label class="section-label">路径策略</label>
        <el-select v-model="nav.strategy.value" class="full-width" size="default">
          <el-option label="最短距离" value="shortest_distance" />
          <el-option label="最短时间" value="shortest_time" />
          <el-option label="避开拥挤" value="avoid_crowd" />
        </el-select>
      </div>

      <div class="panel-section">
        <label class="section-label">交通方式</label>
        <el-radio-group v-model="nav.transportMode.value" size="default">
          <el-radio-button value="walk">步行</el-radio-button>
          <el-radio-button value="bike">骑行</el-radio-button>
          <el-radio-button value="shuttle">接驳车</el-radio-button>
        </el-radio-group>
      </div>

      <!------ Multi-target toggle ------>
      <div class="panel-section">
        <div class="toggle-row">
          <label class="section-label">多目标模式</label>
          <el-switch v-model="isMultiTarget" active-text="多" inactive-text="单" />
        </div>
      </div>

      <!------ Selected nodes display ------>
      <div class="panel-section node-selection">
        <div class="selected-row">
          <span class="selected-label">起点</span>
          <el-tag v-if="startNodeName" type="success" size="small" effect="plain">
            {{ startNodeName }}
          </el-tag>
          <span v-else class="selected-placeholder">未设置</span>
        </div>
        <div class="selected-row">
          <span class="selected-label">终点</span>
          <div class="end-tags" v-if="endNodeNames.length">
            <el-tag
              v-for="(name, idx) in endNodeNames"
              :key="idx"
              type="warning"
              size="small"
              effect="plain"
              closable
              @close="removeEndNode(nav.endNodeIds.value[idx])"
            >
              {{ name }}
            </el-tag>
          </div>
          <span v-else class="selected-placeholder">未设置</span>
        </div>
      </div>

      <!------ Node list search ------>
      <div class="panel-section" v-if="nodes.length">
        <el-input
          v-model="nodeSearch"
          placeholder="搜索节点名称…"
          :prefix-icon="Search"
          clearable
          size="default"
        />
      </div>

      <!------ Node list ------>
      <div class="node-list" v-if="nodes.length">
        <el-scrollbar>
          <div
            v-for="node in filteredNodes"
            :key="node.nodeId"
            class="node-item"
            :class="{
              'is-start': node.nodeId === nav.startNodeId.value,
              'is-end': nav.endNodeIds.value.includes(node.nodeId),
            }"
          >
            <div class="node-info">
              <span class="node-name">{{ node.name }}</span>
              <span class="node-id">#{{ node.nodeId }}</span>
            </div>
            <div class="node-actions">
              <el-button
                size="small"
                text
                type="success"
                :disabled="node.nodeId === nav.startNodeId.value"
                @click="setAsStart(node)"
              >
                <el-icon><Location /></el-icon>
                起点
              </el-button>
              <el-button
                size="small"
                text
                type="warning"
                :disabled="!isMultiTarget && nav.endNodeIds.value.includes(node.nodeId)"
                @click="addAsEnd(node)"
              >
                <el-icon><Location /></el-icon>
                终点
              </el-button>
            </div>
          </div>
        </el-scrollbar>
      </div>
      <div class="panel-section empty-nodes" v-else>
        <p class="empty-hint">尚未加载路网节点</p>
      </div>

      <!------ Plan button ------>
      <div class="panel-section panel-actions">
        <el-button
          type="primary"
          size="large"
          :loading="nav.loading.value"
          :disabled="!nav.startNodeId.value || !nav.endNodeIds.value.length || !nav.scenicAreaId.value"
          class="plan-btn"
          @click="handlePlanRoute"
        >
          规划路线
        </el-button>
      </div>

      <!------ Route info panel ------>
      <div class="route-info" v-if="isrouteReady">
        <el-divider />
        <h4 class="info-title">路线信息</h4>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">距离</span>
            <span class="info-value">{{ nav.distanceKm.value || multiRouteDistance }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">预计时间</span>
            <span class="info-value">{{ nav.estimatedTimeMin.value || multiRouteTime }}</span>
          </div>
          <div class="info-item" v-if="nav.multiRoute.value">
            <span class="info-label">分段数</span>
            <span class="info-value">{{ routeSegmentsCount }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">策略</span>
            <span class="info-value strategy-tag">
              {{ nav.strategy.value === 'shortest_distance' ? '最短距离' : nav.strategy.value === 'shortest_time' ? '最短时间' : '避开拥挤' }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">方式</span>
            <span class="info-value">
              {{ nav.transportMode.value === 'walk' ? '步行' : nav.transportMode.value === 'bike' ? '骑行' : '接驳车' }}
            </span>
          </div>
        </div>
      </div>

      <!------ Action buttons ------>
      <div class="panel-section panel-actions-bottom">
        <el-button :icon="Close" @click="clearRoute" :disabled="!isrouteReady">
          清除路线
        </el-button>
        <el-button :icon="RefreshRight" @click="resetAll" type="danger" plain>
          重置全部
        </el-button>
      </div>
    </aside>

    <!-- ════ MAP AREA ════ -->
    <main class="nav-map">
      <AmapContainer
        ref="mapRef"
        :center="mapCenter"
        :zoom="16"
        @ready="onMapReady"
      />
    </main>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════════
   LAYOUT
   ═══════════════════════════════════════════════ */
.nav-page {
  display: flex;
  height: calc(100vh - 60px);
  overflow: hidden;
}

/* ── Left Panel ── */
.nav-panel {
  width: 300px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #fff);
  border-right: 1px solid var(--el-border-color-light, #e4e7ed);
  overflow: hidden;
}

/* ── Map ── */
.nav-map {
  flex: 1;
  min-width: 0;
  position: relative;
}

/* ═══════════════════════════════════════════════
   PANEL HEADER
   ═══════════════════════════════════════════════ */
.panel-header {
  padding: 20px 16px 12px;
}

.panel-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary, #2c3e50);
  margin: 0 0 4px;
}

.panel-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin: 0;
}

/* ═══════════════════════════════════════════════
   PANEL SECTIONS
   ═══════════════════════════════════════════════ */
.panel-section {
  padding: 8px 16px;
}

.section-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular, #606266);
  margin-bottom: 6px;
}

.load-row {
  display: flex;
  gap: 8px;
}

.scenic-input {
  flex: 1;
}

.full-width {
  width: 100%;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* ═══════════════════════════════════════════════
   NODE SELECTION DISPLAY
   ═══════════════════════════════════════════════ */
.node-selection {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selected-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.selected-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #7f8c8d);
  min-width: 28px;
  padding-top: 2px;
}

.selected-placeholder {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #c0c4cc);
}

.end-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

/* ═══════════════════════════════════════════════
   NODE LIST
   ═══════════════════════════════════════════════ */
.node-list {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.node-list :deep(.el-scrollbar) {
  height: 100%;
}

.node-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  transition: background 0.15s;
}

.node-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.node-item.is-start {
  background: rgba(103, 194, 58, 0.08);
}

.node-item.is-end {
  background: rgba(255, 107, 53, 0.08);
}

.node-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.node-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary, #2c3e50);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-id {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  flex-shrink: 0;
}

.node-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.node-actions .el-button {
  font-size: 12px;
  padding: 4px 6px;
}

.empty-nodes {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

.empty-hint {
  font-size: 13px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin: 0;
}

/* ═══════════════════════════════════════════════
   PLAN BUTTON
   ═══════════════════════════════════════════════ */
.panel-actions {
  padding-bottom: 4px;
}

.plan-btn {
  width: 100%;
}

/* ═══════════════════════════════════════════════
   ROUTE INFO
   ═══════════════════════════════════════════════ */
.route-info {
  padding: 0 16px 8px;
}

.info-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--el-text-color-primary, #2c3e50);
}

.info-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.info-item {
  flex: 1 1 calc(50% - 4px);
  min-width: 100px;
  padding: 8px;
  background: var(--el-fill-color-lighter, #f0f2f5);
  border-radius: 6px;
}

.info-label {
  display: block;
  font-size: 11px;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin-bottom: 2px;
}

.info-value {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: var(--el-color-primary, #ff6b35);
}

.strategy-tag {
  font-size: 12px !important;
  font-weight: 500 !important;
}

/* ═══════════════════════════════════════════════
   BOTTOM ACTIONS
   ═══════════════════════════════════════════════ */
.panel-actions-bottom {
  display: flex;
  gap: 8px;
  padding: 12px 16px 20px;
}

.panel-actions-bottom .el-button {
  flex: 1;
}

/* ═══════════════════════════════════════════════
   RESPONSIVE — MOBILE (<768px)
   ═══════════════════════════════════════════════ */
@media (max-width: 767px) {
  .nav-page {
    flex-direction: column;
    height: auto;
    overflow: auto;
  }

  .nav-panel {
    width: 100%;
    min-width: 0;
    max-height: 50vh;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  }

  .nav-map {
    height: 50vh;
    min-height: 400px;
  }

  .panel-title {
    font-size: 16px;
  }
}
</style>
