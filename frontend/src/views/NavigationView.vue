<script setup lang="ts">
/**
 * NavigationView — Core navigation flow for JourneyCraft.
 * Integrates: node loading, route planning (single + multi-target),
 * polyline drawing (WGS→GCJ conversion), route info panel,
 * map-click interaction, collapsible mobile panel, and session memory.
 */
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
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
// Tab state + config
// ──────────────────────────────────────────────
const activeTab = ref<'route' | 'congestion' | 'facilities' | 'photospots'>('route')

const tabConfigs = [
  { name: 'route' as const, label: '路线', color: '#FF6B35' },
  { name: 'congestion' as const, label: '拥挤度', color: '#E74C3C' },
  { name: 'facilities' as const, label: '设施', color: '#409EFF' },
  { name: 'photospots' as const, label: '拍照点', color: '#E040FB' },
]

const activeTabColor = computed(() => tabConfigs.find((t) => t.name === activeTab.value)?.color || '#FF6B35')

const congestionTabDisabled = computed(() => !nav.scenicAreaId.value)
const facilitiesTabDisabled = computed(() => !nodes.value.length)
const photospotsTabDisabled = computed(() => !nav.scenicAreaId.value)

function getTabDisabled(name: string) {
  if (name === 'congestion') return congestionTabDisabled.value
  if (name === 'facilities') return facilitiesTabDisabled.value
  if (name === 'photospots') return photospotsTabDisabled.value
  return false
}

// ──────────────────────────────────────────────
// Mobile & Panel State
// ──────────────────────────────────────────────
const isMobile = ref(false)
const panelCollapsed = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) panelCollapsed.value = false
}

// ──────────────────────────────────────────────
// Session Memory
// ──────────────────────────────────────────────
const SESSION_KEY = 'journeycraft-last-scenic'
const lastScenicHint = ref('')

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
  if (nodes.value.length > 0) markNodesOnMap()
}

// ──────────────────────────────────────────────
// Scenic ID input & node loading
// ──────────────────────────────────────────────
const scenicIdInput = ref<number | null>(null)
const nodes = ref<PathNode[]>([])
const nodesLoading = ref(false)
const nodesLoadedSuccess = ref(false)
const nodesLoadedCount = ref(0)

async function loadNodes() {
  if (!scenicIdInput.value) {
    ElMessage.warning('请输入景区 ID')
    return
  }
  nodesLoading.value = true
  nodesLoadedSuccess.value = false
  try {
    const res = await request.get<any[]>('/api/navigation/nodes/scenic/' + scenicIdInput.value)
    const apiData = res.data as any
    if (apiData.code !== 200) {
      throw new Error(apiData.message || `请求失败 (${apiData.code})`)
    }
    const data = apiData.data
    nodes.value = (Array.isArray(data) ? data : []).map((n: any) => ({
      ...n,
      nodeId: n.nodeId ?? n.id ?? n.node_id ?? 0,
    })) as PathNode[]
    nav.scenicAreaId.value = scenicIdInput.value

    if (nodes.value.length > 0) {
      const first = nodes.value[0]
      const [lng, lat] = wgs84ToGcj02(first.longitude, first.latitude)
      mapCenter.value = [lng, lat]
    }

    if (mapReady.value) markNodesOnMap()
    nodesLoadedCount.value = nodes.value.length
    nodesLoadedSuccess.value = true
    lastScenicHint.value = ''

    // Persist scenic ID to session
    localStorage.setItem(SESSION_KEY, JSON.stringify({ scenicId: scenicIdInput.value }))

    // Auto-focus node search after nodes load
    await nextTick()
    const searchInput = document.querySelector('.node-search-input input') as HTMLInputElement
    searchInput?.focus()
  } catch (err: any) {
    ElMessage.error('加载节点失败：' + (err?.message || '未知错误'))
  } finally {
    nodesLoading.value = false
  }
}

// ──────────────────────────────────────────────
// Marker Popup (map-click interaction)
// ──────────────────────────────────────────────
const popupNode = ref<PathNode | null>(null)
const popupPixel = ref<{ x: number; y: number } | null>(null)
let popupTimer: ReturnType<typeof setTimeout> | null = null

function handleMarkerClick(node: PathNode, lng: number, lat: number) {
  if (popupTimer) clearTimeout(popupTimer)
  popupNode.value = node
  popupPixel.value = mapRef.value?.lngLatToPixel(lng, lat) ?? null
  // Auto-dismiss after 8s
  popupTimer = setTimeout(() => {
    if (popupNode.value === node) closePopup()
  }, 8000)
}

function closePopup() {
  popupNode.value = null
  popupPixel.value = null
  if (popupTimer) clearTimeout(popupTimer)
}

function setStartFromPopup() {
  if (popupNode.value) setAsStart(popupNode.value)
  closePopup()
}

function setEndFromPopup() {
  if (popupNode.value) addAsEnd(popupNode.value)
  closePopup()
}

// ──────────────────────────────────────────────
// Node markers on map (with click handlers)
// ──────────────────────────────────────────────
function markNodesOnMap() {
  if (!mapRef.value) return
  mapRef.value.clearOverlays()
  closePopup()

  for (const node of nodes.value) {
    const [lng, lat] = wgs84ToGcj02(node.longitude, node.latitude)
    const isStart = node.nodeId === nav.startNodeId.value
    const isEnd = nav.endNodeIds.value.includes(node.nodeId)

    let color = '#909399'
    if (isStart) color = '#67C23A'
    else if (isEnd) color = '#FF6B35'

    mapRef.value.addTextMarker(lng, lat, node.name, color, () => handleMarkerClick(node, lng, lat))
  }
}

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
  nodesLoadedSuccess.value = false
  nodesLoadedCount.value = 0
  mapRef.value?.clearOverlays()
  closePopup()
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
  closePopup()

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
  closePopup()
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
  closePopup()
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
// Tab-switch watch: redraw markers, preserve route overlay
// ──────────────────────────────────────────────
watch(activeTab, (tab) => {
  if (!mapRef.value) return

  switch (tab) {
    case 'route':
      if (nodes.value.length > 0) {
        mapRef.value.clearOverlays()
        closePopup()
        markNodesOnMap()
        if (nav.currentRoute.value) drawSingleRoute()
        if (nav.multiRoute.value) drawMultiRoute()
        mapRef.value.setFitView()
      }
      break
    case 'congestion':
      if (congestedNodes.value.length > 0) {
        mapRef.value.clearOverlays()
        closePopup()
        markCongestionOnMap()
        mapRef.value.setFitView()
      }
      break
    case 'facilities':
      if (nearbyFacilities.value.length > 0) {
        mapRef.value.clearOverlays()
        closePopup()
        markFacilitiesOnMap()
        mapRef.value.setFitView()
      }
      break
    case 'photospots':
      if (photoSpots.value.length > 0) {
        mapRef.value.clearOverlays()
        closePopup()
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

// Route summary for overlays (distance + time + strategy)
const routeSummaryDistance = computed(() => nav.distanceKm.value || multiRouteDistance.value || '')
const routeSummaryTime = computed(() => nav.estimatedTimeMin.value || multiRouteTime.value || '')
const routeSummaryStrategy = computed(() => {
  const s = nav.strategy.value
  if (s === 'shortest_distance') return '最短距离'
  if (s === 'shortest_time') return '最短时间'
  return '避开拥挤'
})
const routeSummaryMode = computed(() => {
  const m = nav.transportMode.value
  if (m === 'walk') return '步行'
  if (m === 'bike') return '骑行'
  return '接驳车'
})

// ── Node color helper (for list badges) ──
function getNodeColor(node: PathNode): string {
  if (node.nodeId === nav.startNodeId.value) return '#67C23A'
  if (nav.endNodeIds.value.includes(node.nodeId)) return '#FF6B35'
  return '#909399'
}

function getNodeBadge(node: PathNode): string | null {
  if (node.nodeId === nav.startNodeId.value) return '已选为起点'
  if (nav.endNodeIds.value.includes(node.nodeId)) return '已选为终点'
  return null
}

// ──────────────────────────────────────────────
// Mobile FAB state
// ──────────────────────────────────────────────
const fabExpanded = ref(false)

function toggleFab() {
  fabExpanded.value = !fabExpanded.value
}

function closeFab() {
  fabExpanded.value = false
}

function fabPlanRoute() {
  closeFab()
  handlePlanRoute()
}

function fabClearRoute() {
  closeFab()
  clearRoute()
}

function fabReset() {
  closeFab()
  resetAll()
}

// ──────────────────────────────────────────────
// Lifecycle
// ──────────────────────────────────────────────
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

  // Restore session memory
  const saved = localStorage.getItem(SESSION_KEY)
  if (saved) {
    try {
      const data = JSON.parse(saved)
      if (data.scenicId) {
        scenicIdInput.value = data.scenicId
        lastScenicHint.value = `上次景区 ID: ${data.scenicId}`
      }
    } catch {
      /* ignore corrupted data */
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<template>
  <div class="nav-page" :class="{ 'panel-collapsed': panelCollapsed }">
    <!-- ════ LEFT PANEL ════ -->
    <aside class="nav-panel" v-show="!panelCollapsed">
      <!------ Header ------>
      <div class="panel-header">
        <h2 class="panel-title">路线导航</h2>
        <p class="panel-desc" v-if="nodes.length">景区已加载 · {{ nodes.length }} 个节点</p>
        <p class="panel-desc" v-else>加载景区路网，规划游览路径</p>
      </div>

      <!-- Tabs with colored active indicator -->
      <div class="nav-tabs" :style="{ '--tab-color': activeTabColor }">
        <el-tabs v-model="activeTab">
          <el-tab-pane
            v-for="tab in tabConfigs"
            :key="tab.name"
            :label="tab.label"
            :name="tab.name"
            :disabled="getTabDisabled(tab.name)"
          />
        </el-tabs>
      </div>

      <!------ Route tab content ------>
      <div v-show="activeTab === 'route'" class="tab-content">
        <!-- ══ WELCOME CARD (when no nodes loaded) ══ -->
        <div v-if="!nodes.length && !nodesLoading" class="welcome-card">
          <div class="welcome-icon">🗺️</div>
          <h3 class="welcome-heading">选择一个景区开始规划路线</h3>
          <p class="welcome-desc">输入景区 ID 加载路网节点，在地图上选择起点和终点即可规划游览路径。</p>

          <div class="welcome-search">
            <div class="welcome-search-row">
              <el-input-number
                v-model="scenicIdInput"
                :min="1"
                :controls="false"
                placeholder="输入景区 ID 加载路网..."
                class="scenic-input"
                size="large"
              />
              <el-button
                type="primary"
                size="large"
                :loading="nodesLoading"
                :icon="Search"
                @click="loadNodes"
              >
                加载路网
              </el-button>
            </div>
            <p class="welcome-hint">💡 示例: 故宫 = 景区 ID 1</p>
            <p v-if="lastScenicHint" class="welcome-hint session-hint">{{ lastScenicHint }}</p>
          </div>
        </div>

        <!-- ══ REGULAR CONTENT (when nodes loaded) ══ -->
        <template v-else>
          <!------ Scenic ID (compact) + success indicator ------>
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
                加载
              </el-button>
            </div>
            <div v-if="nodesLoadedSuccess && !nodesLoading" class="load-success-inline">
              <span class="success-check">✓</span> 已加载 {{ nodesLoadedCount }} 个路网节点
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
              <span v-else class="selected-placeholder">未设置 — 点击地图节点或下方列表</span>
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
              <span v-else class="selected-placeholder">未设置 — 点击地图节点或下方列表</span>
            </div>
          </div>

          <!------ Node list search (auto-focus) ------>
          <div class="panel-section" v-if="nodes.length">
            <el-input
              v-model="nodeSearch"
              placeholder="搜索节点名称…"
              :prefix-icon="Search"
              clearable
              size="default"
              class="node-search-input"
            />
          </div>

          <!------ Node list with colored dots & badges ------>
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
                  <span class="node-dot" :style="{ background: getNodeColor(node) }"></span>
                  <span class="node-name">{{ node.name }}</span>
                  <span class="node-id">#{{ node.nodeId }}</span>
                  <span v-if="getNodeBadge(node)" class="node-badge" :class="{
                    'badge-start': node.nodeId === nav.startNodeId.value,
                    'badge-end': nav.endNodeIds.value.includes(node.nodeId) && node.nodeId !== nav.startNodeId.value,
                  }">
                    {{ getNodeBadge(node) }}
                  </span>
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

          <!------ Route info panel (sidebar) ------>
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
                  {{ routeSummaryStrategy }}
                </span>
              </div>
              <div class="info-item">
                <span class="info-label">方式</span>
                <span class="info-value">{{ routeSummaryMode }}</span>
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
        </template>

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

      <!-- Panel toggle button (mobile) -->
      <button
        v-if="isMobile"
        class="panel-toggle-btn"
        :class="{ collapsed: panelCollapsed }"
        @click="panelCollapsed = !panelCollapsed"
        :title="panelCollapsed ? '展开面板' : '收起面板'"
      >
        <span class="toggle-arrow">{{ panelCollapsed ? '▶' : '◀' }}</span>
      </button>

      <!-- ── Map tools overlay ── -->
      <div class="map-tools">
        <el-button class="fullscreen-btn" :icon="FullScreen" circle size="small" @click="toggleFullscreen" />
        <div v-if="activeTab === 'congestion' && nav.congestionData.value" class="map-legend">
          <span class="legend-item"><span class="legend-dot" style="background:#27AE60"></span>舒适</span>
          <span class="legend-item"><span class="legend-dot" style="background:#F39C12"></span>适中</span>
          <span class="legend-item"><span class="legend-dot" style="background:#E74C3C"></span>拥挤</span>
        </div>
      </div>

      <!-- ── Marker click popup ── -->
      <Transition name="popup-fade">
        <div
          v-if="popupNode && popupPixel"
          class="marker-popup"
          :style="{ left: popupPixel.x + 'px', top: popupPixel.y + 'px' }"
        >
          <div class="popup-header">
            <span class="popup-name">{{ popupNode.name }}</span>
            <span class="popup-id">#{{ popupNode.nodeId }}</span>
            <button class="popup-close" @click="closePopup">×</button>
          </div>
          <div class="popup-actions">
            <button
              class="popup-btn popup-btn-start"
              :disabled="popupNode.nodeId === nav.startNodeId.value"
              @click="setStartFromPopup"
            >
              <span class="popup-dot" style="background:#67C23A"></span>
              设为起点
            </button>
            <button
              class="popup-btn popup-btn-end"
              :disabled="nav.endNodeIds.value.includes(popupNode.nodeId) && !isMultiTarget"
              @click="setEndFromPopup"
            >
              <span class="popup-dot" style="background:#FF6B35"></span>
              设为终点
            </button>
          </div>
          <div class="popup-arrow"></div>
        </div>
      </Transition>

      <!-- ── Route info overlay on map ── -->
      <Transition name="overlay-fade">
        <div v-if="isrouteReady" class="route-overlay-card">
          <div class="overlay-icon">{{ routeSummaryMode === '步行' ? '🚶' : routeSummaryMode === '骑行' ? '🚲' : '🚍' }}</div>
          <div class="overlay-info">
            <div class="overlay-row">
              <span class="overlay-label">{{ routeSummaryMode }}</span>
              <span class="overlay-divider">·</span>
              <span class="overlay-value">{{ routeSummaryTime }}</span>
            </div>
            <div class="overlay-row">
              <span class="overlay-label">📏</span>
              <span class="overlay-value">{{ routeSummaryDistance }}</span>
            </div>
            <div class="overlay-row overlay-strategy">
              策略: {{ routeSummaryStrategy }}
            </div>
          </div>
        </div>
      </Transition>

      <!-- ── Collapsed mini-control (mobile, when panel hidden) ── -->
      <Transition name="overlay-fade">
        <div v-if="isMobile && panelCollapsed && isrouteReady" class="mini-control">
          <div class="mini-info">
            <span>{{ routeSummaryMode === '步行' ? '🚶' : routeSummaryMode === '骑行' ? '🚲' : '🚍' }}</span>
            <span>{{ routeSummaryTime }} · {{ routeSummaryDistance }}</span>
          </div>
          <button class="mini-expand" @click="panelCollapsed = false">展开面板 ▲</button>
        </div>
      </Transition>

      <!-- ── Mobile FAB (floating action button) ── -->
      <div v-if="isMobile && nodes.length" class="mobile-fab-container">
        <Transition name="fab-actions-fade">
          <div v-if="fabExpanded" class="fab-actions">
            <button class="fab-action fab-action-plan" @click="fabPlanRoute">规划</button>
            <button class="fab-action fab-action-clear" @click="fabClearRoute">清除</button>
            <button class="fab-action fab-action-reset" @click="fabReset">重置</button>
          </div>
        </Transition>
        <button class="fab-btn" @click="toggleFab" :class="{ active: fabExpanded }">
          <span v-if="!fabExpanded">
            <template v-if="isrouteReady">{{ routeSummaryTime || '路线' }}</template>
            <template v-else>＋</template>
          </span>
          <span v-else>×</span>
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════════
   CSS Variables
   ═══════════════════════════════════════════════ */
:root {
  --nav-transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

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
  transition: width var(--nav-transition), min-width var(--nav-transition), opacity var(--nav-transition);
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
   WELCOME CARD (initial state)
   ═══════════════════════════════════════════════ */
.welcome-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 20px;
  text-align: center;
}

.welcome-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.8;
}

.welcome-heading {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary, #2c3e50);
  margin: 0 0 8px;
  line-height: 1.4;
}

.welcome-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin: 0 0 20px;
  line-height: 1.5;
  max-width: 260px;
}

.welcome-search {
  width: 100%;
}

.welcome-search-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.welcome-search-row .scenic-input {
  flex: 1;
}

.welcome-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin: 10px 0 0;
}

.session-hint {
  color: var(--el-color-primary, #FF6B35);
  font-weight: 500;
}

/* ═══════════════════════════════════════════════
   INLINE SUCCESS INDICATOR
   ═══════════════════════════════════════════════ */
.load-success-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-success, #67C23A);
  font-weight: 500;
}

.success-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--el-color-success, #67C23A);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
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
   NODE LIST (with dots & badges)
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
  background: rgba(103, 194, 58, 0.06);
}

.node-item.is-end {
  background: rgba(255, 107, 53, 0.06);
}

.node-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
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
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #7f8c8d);
  flex-shrink: 0;
}

.node-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  flex-shrink: 0;
  white-space: nowrap;
  font-weight: 500;
}

.badge-start {
  background: rgba(103, 194, 58, 0.15);
  color: #67C23A;
}

.badge-end {
  background: rgba(255, 107, 53, 0.15);
  color: #FF6B35;
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
   ROUTE INFO (sidebar)
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
   NAV TABS (with colored active indicator)
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

/* Colored active tab using CSS variable */
.nav-tabs :deep(.el-tabs__item.is-active) {
  color: var(--tab-color);
  font-weight: 600;
}

.nav-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--tab-color);
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
   PANEL TOGGLE BUTTON (mobile)
   ═══════════════════════════════════════════════ */
.panel-toggle-btn {
  position: absolute;
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  z-index: 101;
  width: 28px;
  height: 60px;
  border: none;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 0 8px 8px 0;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: left var(--nav-transition);
  padding: 0;
}

.panel-toggle-btn.collapsed {
  left: 0;
}

.toggle-arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary, #7f8c8d);
  line-height: 1;
}

/* ═══════════════════════════════════════════════
   MARKER CLICK POPUP
   ═══════════════════════════════════════════════ */
.marker-popup {
  position: absolute;
  z-index: 200;
  transform: translate(-50%, calc(-100% - 16px));
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  min-width: 180px;
  pointer-events: auto;
}

.popup-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.popup-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #2c3e50);
}

.popup-id {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin-right: auto;
}

.popup-close {
  border: none;
  background: none;
  font-size: 18px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
  border-radius: 4px;
  transition: color 0.15s;
}

.popup-close:hover {
  color: var(--el-text-color-primary, #2c3e50);
  background: var(--el-fill-color-light, #f5f7fa);
}

.popup-actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
}

.popup-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.popup-btn:hover:not(:disabled) {
  border-color: var(--tab-color);
}

.popup-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.popup-btn-start:hover:not(:disabled) {
  background: rgba(103, 194, 58, 0.08);
  border-color: #67C23A;
}

.popup-btn-end:hover:not(:disabled) {
  background: rgba(255, 107, 53, 0.08);
  border-color: #FF6B35;
}

.popup-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.popup-arrow {
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 12px;
  height: 12px;
  background: #fff;
  border-radius: 2px;
  box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.08);
  clip-path: polygon(0 0, 100% 0, 50% 100%);
}

/* popup transitions */
.popup-fade-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.popup-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.popup-fade-enter-from {
  opacity: 0;
  transform: translate(-50%, calc(-100% - 4px));
}

.popup-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, calc(-100% - 24px));
}

/* ═══════════════════════════════════════════════
   ROUTE INFO OVERLAY CARD (on map)
   ═══════════════════════════════════════════════ */
.route-overlay-card {
  position: absolute;
  bottom: 20px;
  left: 16px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 12px;
  padding: 10px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(0, 0, 0, 0.04);
  pointer-events: auto;
}

.overlay-icon {
  font-size: 24px;
  line-height: 1;
}

.overlay-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.overlay-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.overlay-label {
  font-weight: 600;
  color: var(--el-text-color-primary, #2c3e50);
}

.overlay-divider {
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-weight: 400;
}

.overlay-value {
  font-weight: 700;
  color: var(--el-color-primary, #FF6B35);
}

.overlay-strategy {
  font-size: 11px;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin-top: 1px;
}

/* overlay transitions */
.overlay-fade-enter-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.overlay-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.overlay-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.overlay-fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* ═══════════════════════════════════════════════
   MINI-CONTROL (collapsed panel, mobile)
   ═══════════════════════════════════════════════ */
.mini-control {
  position: absolute;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 20px;
  padding: 8px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  pointer-events: auto;
}

.mini-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #2c3e50);
  white-space: nowrap;
}

.mini-expand {
  border: none;
  background: var(--el-color-primary, #FF6B35);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s;
}

.mini-expand:hover {
  opacity: 0.85;
}

/* ═══════════════════════════════════════════════
   MOBILE FAB (floating action button)
   ═══════════════════════════════════════════════ */
.mobile-fab-container {
  position: absolute;
  bottom: 20px;
  right: 16px;
  z-index: 110;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.fab-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.fab-action {
  border: none;
  padding: 8px 14px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  white-space: nowrap;
  transition: transform 0.15s, opacity 0.15s;
}

.fab-action:hover {
  transform: scale(1.05);
}

.fab-action-plan {
  background: var(--el-color-primary, #FF6B35);
  color: #fff;
}

.fab-action-clear {
  background: #fff;
  color: var(--el-text-color-regular, #606266);
  border: 1px solid var(--el-border-color, #dcdfe6);
}

.fab-action-reset {
  background: var(--el-color-danger, #E74C3C);
  color: #fff;
  opacity: 0.85;
}

.fab-btn {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: var(--el-color-primary, #FF6B35);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(255, 107, 53, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease, background 0.15s;
}

.fab-btn:hover {
  transform: scale(1.08);
}

.fab-btn.active {
  background: var(--el-text-color-regular, #606266);
  transform: rotate(45deg);
}

.fab-actions-fade-enter-active {
  transition: all 0.25s ease;
}

.fab-actions-fade-leave-active {
  transition: all 0.2s ease;
}

.fab-actions-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fab-actions-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
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
    transition: max-height var(--nav-transition), opacity var(--nav-transition);
  }

  .nav-page.panel-collapsed .nav-panel {
    max-height: 0;
    opacity: 0;
    overflow: hidden;
  }

  .nav-map {
    height: 50vh;
    min-height: 400px;
    flex: none;
  }

  .nav-page.panel-collapsed .nav-map {
    height: calc(100vh - 60px);
    min-height: 400px;
  }

  .panel-title {
    font-size: 16px;
  }

  /* Move route overlay on mobile when mini-control is present */
  .nav-page.panel-collapsed .route-overlay-card {
    bottom: 80px;
  }

  /* Adjust popup position for smaller screens */
  .marker-popup {
    min-width: 160px;
    max-width: 220px;
  }
}

/* ═══════════════════════════════════════════════
   RESPONSIVE — DESKTOP (>=768px)
   ═══════════════════════════════════════════════ */
@media (min-width: 768px) {
  .panel-toggle-btn,
  .mobile-fab-container,
  .mini-control {
    display: none;
  }
}
</style>
