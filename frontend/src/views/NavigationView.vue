<script setup lang="ts">
/**
 * NavigationView — Core navigation flow for JourneyCraft.
 * Integrates: node loading, route planning (single + multi-target),
 * polyline drawing (WGS→GCJ conversion), and route info panel.
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Location, Close, RefreshRight, FullScreen } from '@element-plus/icons-vue'
import { useNavigation } from '@/composables/useNavigation'
import { request } from '@/api/request'
import { wgs84ToGcj02, pathNodesToGcj02 } from '@/utils/coord'
import AmapContainer from '@/components/map/AmapContainer.vue'
import type { PathNode, NodeCongestion, NearbyFacility, PhotoSpot } from '@/types/navigation'

// ──────────────────────────────────────────────
// Navigation composable
// ──────────────────────────────────────────────
const nav = useNavigation()

// ──────────────────────────────────────────────
// Tab state
// ──────────────────────────────────────────────
const activeTab = ref<'route' | 'congestion' | 'facilities' | 'photospots'>('route')

// ──────────────────────────────────────────────
// Facility type map
// ──────────────────────────────────────────────
const facilityTypeMap: Record<number, { label: string; icon: string }> = {
  0: { label: '卫生间', icon: '🚻' },
  1: { label: '餐饮', icon: '🍴' },
  2: { label: '超市', icon: '🛒' },
  3: { label: '停车场', icon: '🅿️' },
  4: { label: '售票处', icon: '🎫' },
  5: { label: '游客中心', icon: 'ℹ️' },
  6: { label: '医疗点', icon: '🏥' },
  7: { label: 'ATM', icon: '🏧' },
  8: { label: '自动贩卖机', icon: '🥤' },
  9: { label: '摆渡车站', icon: '🚍' },
  10: { label: '自行车租赁', icon: '🚲' },
}

const facilityTypeOptions = Object.entries(facilityTypeMap).map(([value, info]) => ({
  value: Number(value),
  label: `${info.icon} ${info.label}`,
}))

// ──────────────────────────────────────────────
// Congestion helpers
// ──────────────────────────────────────────────
const congestionLevelMap: Record<number, { label: string; markerColor: string; tagType: 'success' | 'warning' | 'danger' }> = {
  0: { label: '舒适', markerColor: '#27AE60', tagType: 'success' },
  1: { label: '适中', markerColor: '#F39C12', tagType: 'warning' },
  2: { label: '拥挤', markerColor: '#E74C3C', tagType: 'danger' },
  3: { label: '严重拥挤', markerColor: '#E74C3C', tagType: 'danger' },
}

const congestionLoading = ref(false)

/** JOIN congestion.nodes with road network nodes */
const congestedNodes = computed(() => {
  if (!nav.congestionData.value || !nodes.value.length) return []
  return nav.congestionData.value.nodes
    .map((cn) => {
      const node = nodes.value.find((n) => n.nodeId === cn.nodeId)
      if (!node) return null
      return { ...cn, name: node.name, latitude: node.latitude, longitude: node.longitude }
    })
    .filter(Boolean) as (NodeCongestion & { name: string; latitude: number; longitude: number })[]
})

const overallCongestionLabel = computed(() => {
  if (!nav.congestionData.value) return ''
  return congestionLevelMap[nav.congestionData.value.overallLevel]?.label || `等级 ${nav.congestionData.value.overallLevel}`
})

const overallCongestionTagType = computed(() => {
  if (!nav.congestionData.value) return 'info'
  const lvl = nav.congestionData.value.overallLevel
  if (lvl >= 2) return 'danger'
  if (lvl === 1) return 'warning'
  return 'success'
})

// ──────────────────────────────────────────────
// Nearby facilities state
// ──────────────────────────────────────────────
const selectedFacilityNodeId = ref<number | null>(null)
const facilityTypeFilter = ref<number | undefined>(undefined)
const nearbyFacilities = ref<NearbyFacility[]>([])
const facilitiesLoading = ref(false)

// ──────────────────────────────────────────────
// Photo spots state
// ──────────────────────────────────────────────
const photoSpots = ref<PhotoSpot[]>([])
const photoSpotsLoading = ref(false)

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
  // Reset advanced features
  nearbyFacilities.value = []
  photoSpots.value = []
  selectedFacilityNodeId.value = null
  facilityTypeFilter.value = undefined
  activeTab.value = 'route'
  ElMessage.success('已重置全部')
}

// ──────────────────────────────────────────────
// Congestion: load & mark
// ──────────────────────────────────────────────
async function loadAndShowCongestion() {
  if (!nav.scenicAreaId.value) {
    ElMessage.warning('请先在路线规划中加载景区路网')
    return
  }
  if (!nodes.value.length) {
    ElMessage.warning('请先在路线规划中加载路网节点')
    return
  }
  congestionLoading.value = true
  try {
    await nav.loadCongestion()
    if (mapRef.value) {
      markCongestionOnMap()
      mapRef.value.setFitView()
    }
    ElMessage.success(`拥挤度已更新（${congestedNodes.value.length} 个节点）`)
  } catch (err: any) {
    ElMessage.error('加载拥挤度失败：' + (err?.message || '未知错误'))
  } finally {
    congestionLoading.value = false
  }
}

function markCongestionOnMap() {
  if (!mapRef.value) return
  mapRef.value.clearOverlays()

  for (const cn of congestedNodes.value) {
    const [lng, lat] = wgs84ToGcj02(cn.longitude, cn.latitude)
    const info = congestionLevelMap[cn.level] || { markerColor: '#909399', label: '未知' }
    const isHeavy = cn.level >= 2

    mapRef.value.addMarker(lng, lat, {
      content: `<div style="background:${info.markerColor};color:white;padding:2px 6px;border-radius:4px;font-size:${isHeavy ? '13px' : '11px'};white-space:nowrap;font-weight:${isHeavy ? '600' : '400'}">${cn.name}</div>`,
    })
  }
}

// ──────────────────────────────────────────────
// Nearby facilities: load & mark
// ──────────────────────────────────────────────
async function loadAndShowFacilities() {
  if (!selectedFacilityNodeId.value) {
    ElMessage.warning('请先选择节点')
    return
  }
  facilitiesLoading.value = true
  try {
    nearbyFacilities.value = await nav.loadNearbyFacilities(
      selectedFacilityNodeId.value,
      facilityTypeFilter.value,
    )
    if (mapRef.value) {
      markFacilitiesOnMap()
    }
    ElMessage.success(`找到 ${nearbyFacilities.value.length} 个附近设施`)
  } catch (err: any) {
    ElMessage.error('查询设施失败：' + (err?.message || '未知错误'))
  } finally {
    facilitiesLoading.value = false
  }
}

function markFacilitiesOnMap() {
  if (!mapRef.value) return
  mapRef.value.clearOverlays()
  for (const fac of nearbyFacilities.value) {
    const [lng, lat] = wgs84ToGcj02(fac.longitude, fac.latitude)
    const typeInfo = facilityTypeMap[fac.type] || { label: '设施', icon: '📍' }
    mapRef.value.addTextMarker(lng, lat, `${typeInfo.icon} ${fac.name}`, '#409EFF')
  }
  mapRef.value.setFitView()
}

// ──────────────────────────────────────────────
// Photo spots: load & mark
// ──────────────────────────────────────────────
async function loadAndShowPhotoSpots() {
  if (!nav.scenicAreaId.value) {
    ElMessage.warning('请先在路线规划中加载景区路网')
    return
  }
  photoSpotsLoading.value = true
  try {
    photoSpots.value = await nav.loadPhotoSpots()
    if (mapRef.value) {
      markPhotoSpotsOnMap()
    }
    ElMessage.success(`加载了 ${photoSpots.value.length} 个拍照点`)
  } catch (err: any) {
    ElMessage.error('加载拍照点失败：' + (err?.message || '未知错误'))
  } finally {
    photoSpotsLoading.value = false
  }
}

function markPhotoSpotsOnMap() {
  if (!mapRef.value) return
  mapRef.value.clearOverlays()
  for (const spot of photoSpots.value) {
    const [lng, lat] = wgs84ToGcj02(spot.latitude, spot.longitude)
    mapRef.value.addTextMarker(lng, lat, `📷 ${spot.name}`, '#E040FB')
  }
  mapRef.value.setFitView()
}

// ──────────────────────────────────────────────
// Fullscreen toggle
// ──────────────────────────────────────────────
const mapContainerRef = ref<HTMLElement | null>(null)

function toggleFullscreen() {
  const el = mapContainerRef.value
  if (!el) return
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    el.requestFullscreen()
  }
}

// ──────────────────────────────────────────────
// Tab-switch watch: redraw markers for current tab
// ──────────────────────────────────────────────
watch(activeTab, (tab) => {
  if (!mapRef.value) return

  switch (tab) {
    case 'route':
      if (nodes.value.length > 0) {
        mapRef.value.clearOverlays()
        markNodesOnMap()
        if (nav.currentRoute.value) drawSingleRoute()
        if (nav.multiRoute.value) drawMultiRoute()
        mapRef.value.setFitView()
      }
      break
    case 'congestion':
      if (congestedNodes.value.length > 0) {
        mapRef.value.clearOverlays()
        markCongestionOnMap()
        mapRef.value.setFitView()
      }
      break
    case 'facilities':
      if (nearbyFacilities.value.length > 0) {
        mapRef.value.clearOverlays()
        markFacilitiesOnMap()
        mapRef.value.setFitView()
      }
      break
    case 'photospots':
      if (photoSpots.value.length > 0) {
        mapRef.value.clearOverlays()
        markPhotoSpotsOnMap()
        mapRef.value.setFitView()
      }
      break
  }
})

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

      <el-tabs v-model="activeTab" class="nav-tabs">
        <el-tab-pane label="路线" name="route" />
        <el-tab-pane label="拥挤度" name="congestion" />
        <el-tab-pane label="设施" name="facilities" />
        <el-tab-pane label="拍照点" name="photospots" />
      </el-tabs>

      <!------ Route tab content ------>
      <div v-show="activeTab === 'route'" class="tab-content">

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

      </div><!-- /route tab content -->

      <!------ Congestion tab content ------>
      <div v-show="activeTab === 'congestion'" class="tab-content">
        <div class="panel-section">
          <p class="panel-desc">查看景区各节点实时拥挤情况</p>
        </div>
        <div class="panel-section">
          <el-button
            type="primary"
            :loading="congestionLoading"
            :disabled="!nav.scenicAreaId.value"
            class="full-width"
            @click="loadAndShowCongestion"
          >
            加载拥挤度
          </el-button>
        </div>
        <div class="panel-section" v-if="nav.congestionData.value">
          <div class="congestion-overall">
            <span class="section-label">整体拥挤度</span>
            <el-tag :type="overallCongestionTagType" effect="dark" size="large">
              {{ overallCongestionLabel }}
            </el-tag>
            <span class="update-time">{{ nav.congestionData.value.updateTime }}</span>
          </div>
          <div class="congestion-legend">
            <span class="legend-item"><span class="legend-dot" style="background:#27AE60"></span>舒适</span>
            <span class="legend-item"><span class="legend-dot" style="background:#F39C12"></span>适中</span>
            <span class="legend-item"><span class="legend-dot" style="background:#E74C3C"></span>拥挤</span>
          </div>
        </div>
        <div class="panel-section" v-if="congestedNodes.length">
          <label class="section-label">拥挤节点 ({{ congestedNodes.length }})</label>
          <el-scrollbar max-height="180px">
            <div v-for="cn in congestedNodes" :key="cn.nodeId" class="facility-item">
              <span class="facility-name">
                <span class="legend-dot" :style="{ background: congestionLevelMap[cn.level]?.markerColor || '#909399' }"></span>
                {{ cn.name }}
              </span>
              <el-tag :type="congestionLevelMap[cn.level]?.tagType || 'info'" size="small">
                {{ congestionLevelMap[cn.level]?.label || '未知' }}
              </el-tag>
            </div>
          </el-scrollbar>
        </div>
      </div>

      <!------ Facilities tab content ------>
      <div v-show="activeTab === 'facilities'" class="tab-content">
        <div class="panel-section">
          <p class="panel-desc">查询选中节点附近的设施</p>
        </div>
        <div class="panel-section">
          <label class="section-label">选择节点</label>
          <el-select
            v-model="selectedFacilityNodeId"
            placeholder="选择路网节点"
            class="full-width"
            filterable
            :disabled="!nodes.length"
          >
            <el-option
              v-for="node in nodes"
              :key="node.nodeId"
              :label="node.name + ' (#' + node.nodeId + ')'"
              :value="node.nodeId"
            />
          </el-select>
        </div>
        <div class="panel-section">
          <label class="section-label">设施类型</label>
          <el-select
            v-model="facilityTypeFilter"
            placeholder="全部类型"
            class="full-width"
            clearable
          >
            <el-option
              v-for="opt in facilityTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
        <div class="panel-section">
          <el-button
            type="primary"
            :loading="facilitiesLoading"
            :disabled="!selectedFacilityNodeId"
            class="full-width"
            @click="loadAndShowFacilities"
          >
            查询附近设施
          </el-button>
        </div>
        <div class="panel-section" v-if="nearbyFacilities.length">
          <label class="section-label">设施列表 ({{ nearbyFacilities.length }})</label>
          <el-scrollbar max-height="200px">
            <div v-for="f in nearbyFacilities" :key="f.id" class="facility-item">
              <span class="facility-name">
                {{ facilityTypeMap[f.type]?.icon || '📍' }} {{ f.name }}
              </span>
              <span class="facility-dist">{{ f.distance }}m</span>
            </div>
          </el-scrollbar>
        </div>
        <div class="panel-section empty-state" v-else-if="!facilitiesLoading && selectedFacilityNodeId">
          <p class="empty-hint">附近暂无设施</p>
        </div>
      </div>

      <!------ Photo spots tab content ------>
      <div v-show="activeTab === 'photospots'" class="tab-content">
        <div class="panel-section">
          <p class="panel-desc">推荐拍照打卡点</p>
        </div>
        <div class="panel-section">
          <el-button
            type="primary"
            :loading="photoSpotsLoading"
            :disabled="!nav.scenicAreaId.value"
            class="full-width"
            @click="loadAndShowPhotoSpots"
          >
            加载拍照点
          </el-button>
        </div>
        <div class="panel-section" v-if="photoSpots.length">
          <label class="section-label">拍照点 ({{ photoSpots.length }})</label>
          <el-scrollbar max-height="260px">
            <div v-for="s in photoSpots" :key="s.id" class="spot-item">
              <div class="spot-name">📷 {{ s.name }}</div>
              <div class="spot-target">主题: {{ s.targetName }}</div>
              <div class="spot-meta">
                <span class="spot-rating">⭐ {{ s.rating }}</span>
                <span class="spot-checkin">{{ s.checkInCount }} 人打卡</span>
              </div>
            </div>
          </el-scrollbar>
        </div>
      </div>

    </aside>

    <!-- ════ MAP AREA ════ -->
    <main class="nav-map" ref="mapContainerRef">
      <AmapContainer
        ref="mapRef"
        :center="mapCenter"
        :zoom="16"
        @ready="onMapReady"
      />
      <div class="map-tools">
        <el-button class="fullscreen-btn" :icon="FullScreen" circle size="small" @click="toggleFullscreen" />
        <div v-if="activeTab === 'congestion' && nav.congestionData.value" class="map-legend">
          <span class="legend-item"><span class="legend-dot" style="background:#27AE60"></span>舒适</span>
          <span class="legend-item"><span class="legend-dot" style="background:#F39C12"></span>适中</span>
          <span class="legend-item"><span class="legend-dot" style="background:#E74C3C"></span>拥挤</span>
        </div>
      </div>
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
   NAV TABS
   ═══════════════════════════════════════════════ */
.nav-tabs {
  flex-shrink: 0;
}

.nav-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  padding: 0 12px;
}

.nav-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

/* ── Tab content ── */
.tab-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
}

/* ═══════════════════════════════════════════════
   CONGESTION TAB
   ═══════════════════════════════════════════════ */
.congestion-overall {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.update-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin-left: auto;
}

.congestion-legend {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

/* ═══════════════════════════════════════════════
   FACILITIES TAB
   ═══════════════════════════════════════════════ */
.facility-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  font-size: 13px;
}

.facility-name {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.facility-dist {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary, #ff6b35);
  flex-shrink: 0;
  margin-left: 8px;
}

/* ═══════════════════════════════════════════════
   PHOTO SPOTS TAB
   ═══════════════════════════════════════════════ */
.spot-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.spot-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #2c3e50);
  margin-bottom: 4px;
}

.spot-target {
  font-size: 12px;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin-bottom: 4px;
}

.spot-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.spot-rating {
  color: #f39c12;
  font-weight: 600;
}

.spot-checkin {
  color: var(--el-text-color-placeholder, #c0c4cc);
}

/* ═══════════════════════════════════════════════
   MAP TOOLS OVERLAY
   ═══════════════════════════════════════════════ */
.map-tools {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.fullscreen-btn {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.map-legend {
  background: rgba(255, 255, 255, 0.95);
  padding: 8px 12px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}

/* ═══════════════════════════════════════════════
   EMPTY STATE
   ═══════════════════════════════════════════════ */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
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
