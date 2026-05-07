<script setup lang="ts">
/**
 * NavigationView — Core navigation flow for JourneyCraft.
 * Integrates: node loading, route planning (single + multi-target),
 * polyline drawing (WGS→GCJ conversion), route info panel,
 * map-click interaction, collapsible mobile panel, and session memory.
 */
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Location, Close, RefreshRight, FullScreen } from '@element-plus/icons-vue'
import { useNavigation } from '@/composables/useNavigation'
import { request } from '@/api/request'
import { scenicApi } from '@/api/modules/scenic'
import { wgs84ToGcj02, pathNodesToGcj02, gcj02ToWgs84 } from '@/utils/coord'
import AmapContainer from '@/components/map/AmapContainer.vue'
import type { PathNode, NodeCongestion, NearbyFacility, PhotoSpot } from '@/types/navigation'
import type { ScenicItem } from '@/types/scenic'

// ── Constants ──
const MOBILE_BREAKPOINT = 768
const DEFAULT_MAP_CENTER: [number, number] = [116.397, 39.916]
const POPUP_AUTO_DISMISS_MS = 8000
const EMPTY_MARKER_TIMEOUT_MS = 2000
const NEARBY_SEARCH_RADIUS = 30   // meters — OSM节点密集，30m足够
const ROUTE_STROKE_WEIGHT = 6

// ──────────────────────────────────────────────
// Navigation composable
// ──────────────────────────────────────────────
const nav = useNavigation()
const route = useRoute()

// ──────────────────────────────────────────────
// Scenic autocomplete suggestion shape
// ──────────────────────────────────────────────
interface ScenicSearchSuggestion {
  value: string
  scenicId: number
}

// Node search result from the API search endpoint
interface NodeSearchResult {
  id?: number
  nodeId?: number
  name: string
  nodeType?: number
}

// Near-by click search result node
interface NearbyClickNode {
  node: PathNode
  distance: number
  label: string
  nodeType: number
  isScenic: boolean
  isPoi: boolean
  _score: number   // 内部评分: labelPriority*1000 - distance
}

// ──────────────────────────────────────────────
// Tab state + config
// ──────────────────────────────────────────────
const activeTab = ref<'route' | 'congestion' | 'facilities' | 'photospots'>('route')

const tabConfigs = [
  { name: 'route' as const, label: '路线', color: 'var(--el-color-primary)' },
  { name: 'congestion' as const, label: '拥挤度', color: '#E74C3C' },
  { name: 'facilities' as const, label: '设施', color: '#409EFF' },
  { name: 'photospots' as const, label: '拍照点', color: '#E040FB' },
]

const activeTabColor = computed(() => tabConfigs.find((t) => t.name === activeTab.value)?.color || 'var(--el-color-primary)')

function getTabDisabled(name: string) {
  if (name === 'congestion') return !nav.scenicAreaId.value
  if (name === 'facilities') return !nodes.value.length
  if (name === 'photospots') return !nav.scenicAreaId.value
  return false
}

// ──────────────────────────────────────────────
// Mobile & Panel State
// ──────────────────────────────────────────────
const isMobile = ref(false)
const panelCollapsed = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
  if (!isMobile.value) panelCollapsed.value = false
}

let resizeTimer: ReturnType<typeof setTimeout> | null = null
function debouncedCheckMobile() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(checkMobile, 150)
}

// ──────────────────────────────────────────────
// Session Memory
// ──────────────────────────────────────────────
const SESSION_KEY = 'journeycraft-last-scenic'
const lastScenicHint = ref('')

// ──────────────────────────────────────────────
// Facility type map
// ──────────────────────────────────────────────
const facilityTypeMap: Record<number, { label: string; icon: string; ariaLabel: string }> = {
  0: { label: '卫生间', icon: '🚻', ariaLabel: '卫生间图标' },
  1: { label: '餐饮', icon: '🍴', ariaLabel: '餐饮图标' },
  2: { label: '超市', icon: '🛒', ariaLabel: '超市图标' },
  3: { label: '停车场', icon: '🅿️', ariaLabel: '停车场图标' },
  4: { label: '售票处', icon: '🎫', ariaLabel: '售票处图标' },
  5: { label: '游客中心', icon: 'ℹ️', ariaLabel: '游客中心图标' },
  6: { label: '医疗点', icon: '🏥', ariaLabel: '医疗点图标' },
  7: { label: 'ATM', icon: '🏧', ariaLabel: 'ATM图标' },
  8: { label: '自动贩卖机', icon: '🥤', ariaLabel: '自动贩卖机图标' },
  9: { label: '摆渡车站', icon: '🚍', ariaLabel: '摆渡车站图标' },
  10: { label: '自行车租赁', icon: '🚲', ariaLabel: '自行车租赁图标' },
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
      const node = nodeMap.value.get(cn.nodeId)
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
// Map click → context menu (replaces pick mode)
// ──────────────────────────────────────────────
const clickMenuNode = ref<PathNode | null>(null)
const clickMenuDistance = ref(0)
const clickMenuPos = ref<{ x: number; y: number } | null>(null)

const nearbyClickNodes = ref<NearbyClickNode[]>([])
const nearbyClickPos = ref<{ x: number; y: number } | null>(null)

// ──────────────────────────────────────────────
// Map
// ──────────────────────────────────────────────
const mapRef = ref<InstanceType<typeof AmapContainer> | null>(null)
const mapReady = ref(false)
const mapCenter = ref<[number, number]>(DEFAULT_MAP_CENTER)

function onMapReady() {
  mapReady.value = true
  if (nodes.value.length > 0) markNodesOnMap()
}

// ──────────────────────────────────────────────
// Scenic ID input & node loading
// ──────────────────────────────────────────────
const scenicIdInput = ref<number | null>(null)
const searchKeyword = ref('')
/**
 * ── Display nodes vs. routing node map ──────────────────────────────────
 * nodes.value  = display list: ONE representative node per scenic area.
 *                Used for map markers (markNodesOnMap) and the sidebar list.
 *                This is a cumulative list — loading additional scenic areas
 *                appends their representative nodes without clearing existing.
 * nodeMap.value = routing source: ALL routeable nodes keyed by nodeId.
 *                Used for route planning (Dijkstra), setAsStart / addAsEnd,
 *                node lookups, and the context menu "nearby node" search.
 *                This map is also cumulative across scenic area loads.
 * ────────────────────────────────────────────────────────────────────────
 * This separation avoids displaying hundreds of routing graph nodes on the
 * map while keeping the full connectivity graph available for pathfinding.
 * ────────────────────────────────────────────────────────────────────────
 */
const nodes = ref<PathNode[]>([])
const nodesLoading = ref(false)
const nodesLoadedSuccess = ref(false)
const nodesLoadedCount = ref(0)
const nodeMap = ref<Map<number, PathNode>>(new Map())
const nodeGcjCoords = ref<Map<number, [number, number]>>(new Map())

/** Number of scenic areas currently loaded (one display node per area). */
const displayCount = computed(() => nodes.value.length)

// ── Node name search (API-backed) ──
const nodeSearchKeyword = ref('')
const nodeSearchResults = ref<NodeSearchResult[]>([])
let nodeSearchTimer: ReturnType<typeof setTimeout> | null = null

/** Last map click position (WGS-84) for smart search result sorting by proximity. */
const lastClickPosition = ref<{ lng: number; lat: number } | null>(null)

async function loadNodes() {
  if (!scenicIdInput.value) {
    ElMessage.warning('请输入景区 ID')
    return
  }
  // ★ 累积加载而非清除 — 保留已加载景区的节点数据
  // 不清除旧数据，避免已选起点/终点的标签从景区名退化到节点编号
  nodesLoading.value = true
  nodesLoadedSuccess.value = false
  try {
    const res = await request.get<PathNode[]>('/api/navigation/nodes/scenic/' + scenicIdInput.value)
    const apiData = res.data as any
    if (apiData.code !== 200) {
      throw new Error(apiData.message || `请求失败 (${apiData.code})`)
    }
    const data = apiData.data
    // ★ 使用局部变量存储原始节点，避免污染显示列表 nodes.value
    const rawNodes = (Array.isArray(data) ? data : []).map((n: Record<string, unknown>) => ({
      ...n,
      nodeId: (n['nodeId'] as number) ?? (n['id'] as number) ?? (n['node_id'] as number) ?? 0,
    })) as PathNode[]

    // ★ 智能选择代表节点：优先景区入口 > POI > 有名节点 > 普通节点
    // 对一个景区只显示最优节点在地图上，但保留路由所需的全部连通性
    const scenicAreaMap = new Map<number, PathNode>()
    for (const node of rawNodes) {
      if (!node.scenicAreaId) {
        scenicAreaMap.set(node.nodeId, node)
        continue
      }
      const existing = scenicAreaMap.get(node.scenicAreaId)
      if (!existing) {
        scenicAreaMap.set(node.scenicAreaId, node)
        continue
      }
      // 评分：isPrimary=100, entrance=80, POI=60, important=40, default=0
      const score = (n: any) =>
        (n.isPrimary ? 100 : 0) + (n.nodeType === 0 ? 80 : n.nodeType === 2 ? 60 : (n.isImportant || n.important) ? 40 : 0)
      if (score(node) > score(existing)) {
        scenicAreaMap.set(node.scenicAreaId, node)
      }
    }
    // ★ 累积合并到全局路由图 — 不清除已有数据，保证已选起点/终点的标签不会丢失
    for (const n of rawNodes) {
      if (!nodeMap.value.has(n.nodeId) || (n.scenicAreaName && !nodeMap.value.get(n.nodeId)?.scenicAreaName)) {
        nodeMap.value.set(n.nodeId, n)
      }
      const gcjCoords = wgs84ToGcj02(n.longitude, n.latitude)
      nodeGcjCoords.value.set(n.nodeId, gcjCoords)
    }
    // 累积显示节点：替换同名景区（避免重复标签），追加新景区
    for (const dn of scenicAreaMap.values()) {
      const existingIdx = nodes.value.findIndex(
        existing => existing.scenicAreaId != null && existing.scenicAreaId === dn.scenicAreaId
      )
      if (existingIdx >= 0) {
        nodes.value[existingIdx] = dn  // 替换旧的同景区节点
      } else if (!nodes.value.some(existing => existing.nodeId === dn.nodeId)) {
        nodes.value.push(dn)  // 追加新景区
      }
    }
    nav.scenicAreaId.value = scenicIdInput.value

    // ★ 跳转到新加载的景区 (使用 rawNodes, 而非累计列表中的第一个)
    if (rawNodes.length > 0) {
      const [lng, lat] = wgs84ToGcj02(rawNodes[0].longitude, rawNodes[0].latitude)
      mapCenter.value = [lng, lat]
    }

    if (mapReady.value) {
      markNodesOnMap()
      // Explicitly move the map to new scenic area (not the first loaded one)
      if (rawNodes.length > 0) {
        const [lng, lat] = wgs84ToGcj02(rawNodes[0].longitude, rawNodes[0].latitude)
        const map = mapRef.value?.getMap()
        if (map) { map.setCenter([lng, lat]); map.setZoom(16) }
      }
    }
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

/**
 * Approximate haversine distance in meters (fast, no trig heap allocations).
 * Accurate to ~0.5% for distances < 100 km at mid-latitudes.
 */
function haversineApprox(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const dLat = (lat2 - lat1) * 111320
  const dLng = (lng2 - lng1) * 111320 * Math.cos(lat1 * Math.PI / 180)
  return Math.sqrt(dLat * dLat + dLng * dLng)
}

async function querySearchScenic(queryString: string, cb: (results: ScenicSearchSuggestion[]) => void) {
  if (!queryString || queryString.length < 1) { cb([]); return }
  try {
    const res = await scenicApi.search({ keyword: queryString, page: 1, size: 10 })
    const apiData = res.data as any
    if (apiData.code !== 200) { cb([]); return }
    let list = apiData.data?.list || []

    // Sort by distance from last click position if available
    if (lastClickPosition.value && list.length > 0) {
      const { lat, lng } = lastClickPosition.value
      list.sort((a: any, b: any) => {
        const distA = haversineApprox(lat, lng, a.latitude, a.longitude)
        const distB = haversineApprox(lat, lng, b.latitude, b.longitude)
        return distA - distB
      })
    }

    const results = list.map((item: ScenicItem) => ({
      value: `${item.name} (${item.city || '未知城市'})`,
      scenicId: item.id,
    }))
    cb(results)
  } catch {
    ElMessage.warning('景区搜索暂不可用')
    cb([])
  }
}

function handleScenicSelect(item: ScenicSearchSuggestion) {
  scenicIdInput.value = item.scenicId
  searchKeyword.value = item.value
  // Clear stale node search results before loading new scenic
  nodeSearchResults.value = []
  nodeSearchKeyword.value = ''
  if (item.scenicId) loadNodes()
}

// ──────────────────────────────────────────────
// Marker Popup (map-click interaction)
// ──────────────────────────────────────────────
const popupNode = ref<PathNode | null>(null)
const popupPixel = ref<{ x: number; y: number } | null>(null)
let popupTimer: ReturnType<typeof setTimeout> | null = null
let emptyClickMarker: any | null = null  // AMap.Marker instance

function handleMarkerClick(node: PathNode, lng: number, lat: number) {
  if (popupTimer) clearTimeout(popupTimer)
  popupNode.value = node
  popupPixel.value = mapRef.value?.lngLatToPixel(lng, lat) ?? null
  // Auto-dismiss after 8s
  popupTimer = setTimeout(() => {
    if (popupNode.value === node) closePopup()
  }, POPUP_AUTO_DISMISS_MS)
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
// Map click → context menu (replaces pick mode)
// ──────────────────────────────────────────────
function onMapClick(lnglat: [number, number]) {
  closeMenu()
  closePopup()

  // Convert GCJ-02 (AMap coordinate) to WGS-84 for node lookup
  const [wgsLng, wgsLat] = gcj02ToWgs84(lnglat[0], lnglat[1])

  // Save click position for smart search result sorting by proximity
  lastClickPosition.value = { lng: wgsLng, lat: wgsLat }

  // Find nearby nodes in already-loaded routing graph
  const nearby = findNearbyNodesGrouped(wgsLng, wgsLat)

  if (nearby.length > 0) {
    nearbyClickNodes.value = nearby
    nearbyClickPos.value = mapRef.value?.lngLatToPixel(lnglat[0], lnglat[1]) ?? null

    // Auto-center map on the first (closest) node
    const [gcjLng, gcjLat] = wgs84ToGcj02(nearby[0].node.longitude, nearby[0].node.latitude)
    const map = mapRef.value?.getMap()
    if (map) map.setCenter([gcjLng, gcjLat])
  } else {
    // Show empty click indicator
    if (emptyClickMarker) {
      try { mapRef.value?.getMap()?.remove(emptyClickMarker) } catch {}
      emptyClickMarker = null
    }
    const marker = mapRef.value?.addMarker(lnglat[0], lnglat[1], {
      content: '<div style="width:12px;height:12px;background:var(--el-color-primary);border-radius:50%;opacity:0.6"></div>',
    })
    if (marker) {
      emptyClickMarker = marker
      setTimeout(() => {
        if (emptyClickMarker) {
          try { mapRef.value?.getMap()?.remove(emptyClickMarker) } catch {}
          emptyClickMarker = null
        }
      }, EMPTY_MARKER_TIMEOUT_MS)
    }
    ElMessage.info('该位置附近未找到路网节点')
  }
}

function menuSetStart() {
  if (clickMenuNode.value) { setAsStart(clickMenuNode.value); closeMenu() }
}
function menuSetEnd() {
  if (clickMenuNode.value) { addAsEnd(clickMenuNode.value); closeMenu() }
}
function menuAddWaypoint() {
  if (clickMenuNode.value) { addAsEnd(clickMenuNode.value); closeMenu() }
}
function menuFocusNode() {
  if (clickMenuNode.value) {
    const [lng, lat] = wgs84ToGcj02(clickMenuNode.value.longitude, clickMenuNode.value.latitude)
    const map = mapRef.value?.getMap()
    if (map) { map.setCenter([lng, lat]); map.setZoom(17) }
  }
  closeMenu()
}
function closeMenu() {
  clickMenuNode.value = null
  clickMenuPos.value = null
  nearbyClickNodes.value = []
  nearbyClickPos.value = null
}

// ── Smart nearby node search (used by onMapClick) ──
function findNearbyNodesGrouped(wgsLng: number, wgsLat: number): NearbyClickNode[] {
  const results: NearbyClickNode[] = []
  const avgLat = (wgsLat * Math.PI) / 180
  const cosLat = Math.cos(avgLat)

  for (const [_nodeId, node] of nodeMap.value) {
    const dx = (node.longitude - wgsLng) * 111320 * cosLat
    const dy = (node.latitude - wgsLat) * 111320
    const dist = Math.sqrt(dx * dx + dy * dy)

    if (dist <= NEARBY_SEARCH_RADIUS) {
      // 标签优先级评分: scenic=100, POI=80, named=60, fallback=0
      const n = node as any
      const labelScore = node.scenicAreaName ? 100 
        : (n.nodeType === 2 && node.name) ? 80 
        : node.name ? 60 
        : 0
      results.push({
        node,
        distance: Math.round(dist),
        label: getSmartClickLabel(node),
        nodeType: n.nodeType ?? 5,
        isScenic: !!node.scenicAreaId,
        isPoi: (n.nodeType === 2),
        _score: labelScore * 1000 - dist, // 高分标签优先，距离近的优先
      })
    }
  }

  // 按综合评分降序（标签优先级高 + 距离近 = 排前面）
  results.sort((a, b) => (b as any)._score - (a as any)._score)
  return results.slice(0, 1) // 只返回最优的一个
}

function getSmartClickLabel(node: PathNode): string {
  // Priority: scenic area > POI name > node name > fallback
  if (node.scenicAreaName) return node.scenicAreaName
  const n = node as any
  if (n.nodeType === 2 && node.name) return node.name  // POI
  if (node.name) return node.name
  return `节点 #${node.nodeId}`
}

function setStartFromNearby(item: NearbyClickNode) {
  setAsStart(item.node)
  closeMenu()
}

function setEndFromNearby(item: NearbyClickNode) {
  addAsEnd(item.node)
  closeMenu()
}

// ──────────────────────────────────────────────
// Unified marker renderer — handles clearOverlays,
// closePopup, GCJ-02 coord lookup (precomputed with
// fallback to on-the-fly conversion), and error wrapping.
// ──────────────────────────────────────────────
function renderMarkersOnMap<T extends { longitude?: number; latitude?: number }>(
  items: T[],
  renderFn: (item: T, gcjLng: number, gcjLat: number) => void,
) {
  try {
    const map = mapRef.value
    if (!map) return
    map.clearOverlays()
    closePopup()
    for (const item of items) {
      const nodeId = (item as any).nodeId ?? (item as any).id
      let coords: [number, number] | undefined
      if (nodeId != null) {
        coords = nodeGcjCoords.value.get(nodeId)
      }
      if (!coords) {
        if (item.longitude == null || item.latitude == null) continue
        coords = wgs84ToGcj02(item.longitude, item.latitude)
      }
      const [gcjLng, gcjLat] = coords
      renderFn(item, gcjLng, gcjLat)
    }
  } catch (err) {
    console.error('[Nav] renderMarkersOnMap failed:', err)
  }
}

// ──────────────────────────────────────────────
// Node markers on map (with click handlers)
// ──────────────────────────────────────────────
function markNodesOnMap() {
  renderMarkersOnMap(nodes.value, (node, lng, lat) => {
    const isStart = node.nodeId === nav.startNodeId.value
    const isEnd = nav.endNodeIds.value.includes(node.nodeId)

    let color = '#909399'
    if (isStart) color = '#67C23A'
    else if (isEnd) color = '#E74C3C'

    const displayName = node.scenicAreaName || node.name
    mapRef.value?.addTextMarker(lng, lat, displayName, color, () => handleMarkerClick(node, lng, lat))
  })
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
  return nodes.value.filter((n) => {
    const displayName = n.scenicAreaName || n.name
    return displayName.toLowerCase().includes(kw)
  })
})

// ──────────────────────────────────────────────
// Node selection helpers
// ──────────────────────────────────────────────
function setAsStart(node: PathNode) {
  nav.setStartNode(node.nodeId)
  if (node.scenicAreaId) {
    nav.startScenicAreaId.value = node.scenicAreaId
  }
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
  // ★ Set endScenicAreaId for scenic-area-level route calculation
  if (node.scenicAreaId) {
    nav.endScenicAreaId.value = node.scenicAreaId
  }
  markNodesOnMap()
}

function removeEndNode(nodeId: number) {
  nav.removeEndNode(nodeId)
  if (nav.endNodeIds.value.length === 0) {
    nav.endScenicAreaId.value = null
  }
  markNodesOnMap()
}

// ──────────────────────────────────────────────
// Multi-target mode
// ──────────────────────────────────────────────
const isMultiTarget = ref(false)

// ──────────────────────────────────────────────
// Route planning
// ──────────────────────────────────────────────
const segmentColors = ['#33E9D1', '#3498DB', '#2ECC71', '#F39C12', '#9B59B6']

async function handlePlanRoute() {
  if (nav.loading.value) return
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
  try {
    if (!mapRef.value || !nav.routePath.value) return
    mapRef.value.clearOverlays()
    markNodesOnMap()
    mapRef.value.drawPolyline(nav.routePath.value, {
      strokeColor: '#33E9D1',
      strokeWeight: ROUTE_STROKE_WEIGHT,
      showDir: true,
    })
    mapRef.value.setFitView()
  } catch (err) {
    console.error('[Nav] drawSingleRoute failed:', err)
  }
}

function drawMultiRoute() {
  try {
    if (!mapRef.value || !nav.multiRoute.value) return
    mapRef.value.clearOverlays()
    markNodesOnMap()

    const segments = nav.multiRoute.value.segments
    for (let i = 0; i < segments.length; i++) {
      const path = pathNodesToGcj02(segments[i].nodes)
      mapRef.value.drawPolyline(path, {
        strokeColor: segmentColors[i % segmentColors.length],
        strokeWeight: ROUTE_STROKE_WEIGHT,
        showDir: true,
      })
    }
    mapRef.value.setFitView()
  } catch (err) {
    console.error('[Nav] drawMultiRoute failed:', err)
  }
}

// ──────────────────────────────────────────────
// Clear / Reset
// ──────────────────────────────────────────────
function clearRoute() {
  try {
    mapRef.value?.clearOverlays()
    markNodesOnMap()
    nav.currentRoute.value = null
    nav.multiRoute.value = null
    ElMessage.success('路线已清除')
  } catch (err) {
    console.error('[Nav] clearRoute failed:', err)
  }
}

async function confirmReset() {
  try {
    await ElMessageBox.confirm('确定要重置全部吗？这将清除已加载的景区、路线和所有选择。', '确认重置', {
      confirmButtonText: '确定重置',
      cancelButtonText: '取消',
      type: 'warning',
    })
    resetAll()
  } catch { /* user cancelled */ }
}

function resetAll() {
  try {
    nav.reset()
    nav.scenicAreaId.value = null  // clear stale scenic ID
    nodes.value = []
    nodeMap.value = new Map()
    nodeGcjCoords.value = new Map()
    scenicIdInput.value = null
    nodesLoadedSuccess.value = false
    nodesLoadedCount.value = 0
    mapRef.value?.clearOverlays()
    closePopup()
    closeMenu()
    nearbyFacilities.value = []
    photoSpots.value = []
    selectedFacilityNodeId.value = null
    facilityTypeFilter.value = undefined
    activeTab.value = 'route'
    // Clear stale search state
  } catch (err) {
    console.error('[Nav] resetAll failed:', err)
  }
  searchKeyword.value = ''
  nodeSearch.value = ''
  nodeSearchResults.value = []
  nodeSearchKeyword.value = ''
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
  renderMarkersOnMap(congestedNodes.value, (cn, lng, lat) => {
    const info = congestionLevelMap[cn.level] || { markerColor: '#909399', label: '未知' }
    const isHeavy = cn.level >= 2
    mapRef.value?.addMarker(lng, lat, {
      content: `<div style="background:${info.markerColor};color:white;padding:2px 6px;border-radius:4px;font-size:${isHeavy ? '13px' : '11px'};white-space:nowrap;font-weight:${isHeavy ? '600' : '400'}">${cn.name}</div>`,
    })
  })
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
  renderMarkersOnMap(nearbyFacilities.value, (fac, lng, lat) => {
    const typeInfo = facilityTypeMap[fac.type] || { label: '设施', icon: '📍' }
    mapRef.value?.addTextMarker(lng, lat, `${typeInfo.icon} ${fac.name}`, '#409EFF')
  })
  mapRef.value?.setFitView()
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
  renderMarkersOnMap(photoSpots.value, (spot, lng, lat) => {
    mapRef.value?.addTextMarker(lng, lat, `📷 ${spot.name}`, '#E040FB')
  })
  mapRef.value?.setFitView()
}

// ──────────────────────────────────────────────
// Fullscreen toggle
// ──────────────────────────────────────────────
const mapContainerRef = ref<HTMLElement | null>(null)

function toggleFullscreen() {
  const el = mapContainerRef.value
  if (!el) return
  try {
    if (document.fullscreenElement) {
      document.exitFullscreen()
    } else {
      el.requestFullscreen()
    }
  } catch (err) {
    console.error('[Nav] Fullscreen toggle failed:', err)
  }
}

// ──────────────────────────────────────────────
// Tab-switch watch: redraw markers, preserve route overlay
// ──────────────────────────────────────────────
watch(activeTab, (tab) => {
  nodeSearchResults.value = []
  nodeSearchKeyword.value = ''
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
  if (nav.startScenicAreaId.value) {
    // When start is a scenic area, find its representative node's scenicAreaName
    const node = nodeMap.value.get(nav.startNodeId.value)
    return node?.scenicAreaName || node?.name || `景区 #${nav.startScenicAreaId.value}`
  }
  const node = nodeMap.value.get(nav.startNodeId.value)
  return node?.scenicAreaName || node?.name || `节点 #${nav.startNodeId.value}`
})

/** Always shows scenic area name when the start was chosen via scenic-area-level selection. */
const startScenicName = computed(() => {
  if (!nav.startScenicAreaId.value) return null
  const node = nodeMap.value.get(nav.startNodeId.value!)
  return node?.scenicAreaName || null
})

const endNodeNames = computed(() =>
  nav.endNodeIds.value.map((id) => {
    const node = nodeMap.value.get(id)
    return node?.scenicAreaName || node?.name || `节点 #${id}`
  }),
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
  if (nav.endNodeIds.value.includes(node.nodeId)) return '#E74C3C'
  return '#909399'
}

function getNodeBadge(node: PathNode): string | null {
  if (node.nodeId === nav.startNodeId.value) return '已选为起点'
  if (nav.endNodeIds.value.includes(node.nodeId)) return '已选为终点'
  return null
}

// ──────────────────────────────────────────────
// Advanced toggle: show/hide node list
// ──────────────────────────────────────────────
const showNodeList = ref(false)

function getCurrentScenicName(): string {
  return nodes.value[0]?.scenicAreaName || '当前景区'
}

function clearStart() {
  nav.startNodeId.value = null
  nav.startScenicAreaId.value = null
  markNodesOnMap()
}

function clearEnd() {
  nav.endNodeIds.value = []
  nav.endScenicAreaId.value = null
  markNodesOnMap()
}

function swapStartEnd() {
  const tempNodeId = nav.startNodeId.value
  const tempScenicId = nav.startScenicAreaId.value
  nav.startNodeId.value = nav.endNodeIds.value[0] || null
  nav.startScenicAreaId.value = nav.endScenicAreaId.value
  nav.endNodeIds.value = tempNodeId ? [tempNodeId] : []
  nav.endScenicAreaId.value = tempScenicId
  markNodesOnMap()
}

function handleStartSelect(item: ScenicSearchSuggestion) {
  scenicIdInput.value = item.scenicId
  searchKeyword.value = item.value
  loadNodes().then(() => {
    if (nodes.value.length > 0) {
      setAsStart(nodes.value[0])
    }
  })
}

function handleEndSelect(item: ScenicSearchSuggestion) {
  scenicIdInput.value = item.scenicId
  searchKeyword.value = item.value
  loadNodes().then(() => {
    if (nodes.value.length > 0) {
      addAsEnd(nodes.value[0])
    }
  })
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
  confirmReset()
}

// ──────────────────────────────────────────────
// Keyboard handler
// ──────────────────────────────────────────────
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    closeMenu()
    closePopup()
  }
}

// ──────────────────────────────────────────────
// Lifecycle
// ──────────────────────────────────────────────
onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', debouncedCheckMobile)
  window.addEventListener('keydown', handleKeydown)

  // Check route param first (takes priority)
  const scenicIdFromRoute = route.params.scenicId
  const hasRouteParam = scenicIdFromRoute && !isNaN(Number(scenicIdFromRoute))

  // Restore session memory
  const saved = localStorage.getItem(SESSION_KEY)
  if (saved) {
    try {
      const data = JSON.parse(saved)
      if (data.scenicId) {
        scenicIdInput.value = data.scenicId
        lastScenicHint.value = `上次景区 ID: ${data.scenicId}`
        // Auto-load from session only if no route param
        if (!hasRouteParam) {
          loadNodes()
        }
      }
    } catch {
      /* ignore corrupted data */
    }
  }

  // Auto-load from route param if present
  if (hasRouteParam) {
    scenicIdInput.value = Number(scenicIdFromRoute)
    await loadNodes()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', debouncedCheckMobile)
  window.removeEventListener('keydown', handleKeydown)
  if (nodeSearchTimer) clearTimeout(nodeSearchTimer)
  if (popupTimer) clearTimeout(popupTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
  if (emptyClickMarker && mapRef.value) {
    try { mapRef.value.getMap()?.remove(emptyClickMarker) } catch { /* ignore */ }
  }
})
</script>

<template>
  <div class="nav-page" :class="{ 'panel-collapsed': panelCollapsed }">
    <!-- ════ LEFT PANEL ════ -->
    <aside class="nav-panel" v-show="!panelCollapsed">
      <!------ Header ------>
      <div class="panel-header">
        <h2 class="panel-title">路线导航</h2>
        <p class="panel-desc" v-if="nodes.length">{{ getCurrentScenicName() }} · {{ displayCount }} 个景区已加载</p>
        <p class="panel-desc" v-else>搜索景区名称，规划游览路径</p>
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
          <h3 class="welcome-heading">探索你的旅程</h3>
          <p class="welcome-desc">搜索景区名称，规划游览路线</p>

          <div class="welcome-search">
            <el-autocomplete
              v-model="searchKeyword"
              :fetch-suggestions="querySearchScenic"
              placeholder="搜索景区名称..."
              :trigger-on-focus="false"
              prefix-icon="Search"
              size="large"
              class="scenic-search-input"
              @select="handleScenicSelect"
            />
            <div class="example-chips">
              <span class="example-chip" @click="searchKeyword='故宫'; querySearchScenic('故宫', (r) => { if(r.length) handleScenicSelect(r[0]) })">故宫</span>
              <span class="example-chip" @click="searchKeyword='颐和园'; querySearchScenic('颐和园', (r) => { if(r.length) handleScenicSelect(r[0]) })">颐和园</span>
              <span class="example-chip" @click="searchKeyword='八达岭'; querySearchScenic('八达岭', (r) => { if(r.length) handleScenicSelect(r[0]) })">八达岭长城</span>
            </div>
          </div>
        </div>

        <!-- ══ REGULAR CONTENT (when nodes loaded) ══ -->
        <template v-else>
          <!------ Current Scenic Bar ------>
          <div class="current-scenic-bar">
            <span class="current-scenic-name">🏔️ {{ getCurrentScenicName() }}</span>
            <span class="current-scenic-count">· {{ displayCount }} 个景区已加载</span>
            <el-autocomplete
              v-model="searchKeyword"
              :fetch-suggestions="querySearchScenic"
              placeholder="切换景区..."
              :trigger-on-focus="false"
              prefix-icon="Search"
              size="small"
              class="scenic-switch-input"
              @select="handleScenicSelect"
            />
          </div>

          <!------ FROM→TO Selection Cards ------>
          <div class="fromto-section">
            <!-- Start Point -->
            <div class="fromto-card start-card">
              <div class="fromto-label">🚩 起点</div>
              <div class="fromto-value" v-if="startNodeName">
                <span class="fromto-name">{{ startScenicName || startNodeName }}</span>
                <el-button text size="small" class="fromto-clear" @click="clearStart()">✕</el-button>
              </div>
              <el-autocomplete
                v-else
                v-model="searchKeyword"
                :fetch-suggestions="querySearchScenic"
                placeholder="搜索起点景区..."
                :trigger-on-focus="false"
                size="small"
                @select="handleStartSelect"
              />
            </div>

            <!-- Swap Button -->
            <div class="fromto-swap" v-if="startNodeName && endNodeNames.length" @click="swapStartEnd()">
              ⇅ 交换
            </div>

            <!-- End Point -->
            <div class="fromto-card end-card">
              <div class="fromto-label">🏁 终点</div>
              <div class="fromto-value" v-if="endNodeNames.length">
                <span class="fromto-name">{{ endNodeNames[0] }}</span>
                <el-button text size="small" class="fromto-clear" @click="clearEnd()">✕</el-button>
                <div class="extra-ends" v-if="endNodeNames.length > 1">
                  <span v-for="(name, idx) in endNodeNames.slice(1)" :key="idx" class="extra-end-tag">
                    {{ name }}
                    <el-button text size="small" @click="removeEndNode(nav.endNodeIds.value[idx + 1])">✕</el-button>
                  </span>
                </div>
              </div>
              <el-autocomplete
                v-else
                v-model="searchKeyword"
                :fetch-suggestions="querySearchScenic"
                placeholder="搜索终点景区..."
                :trigger-on-focus="false"
                size="small"
                @select="handleEndSelect"
              />
            </div>
          </div>

          <!------ Route info — prominent card (when route is planned) ------>
          <div class="route-result-card" v-if="isrouteReady">
            <div class="route-result-header">✅ 路线规划完成</div>
            <div class="route-result-body">
              <div class="route-result-path">
                🚩 {{ startNodeName }} → 🏔️ {{ endNodeNames[0] || '终点' }}
              </div>
              <div class="route-result-meta">
                <span class="route-result-stat">
                  <span class="stat-label">距离</span>
                  <span class="stat-value">{{ nav.distanceKm.value || multiRouteDistance || '—' }}</span>
                </span>
                <span class="route-result-stat">
                  <span class="stat-label">预计</span>
                  <span class="stat-value">{{ nav.estimatedTimeMin.value || multiRouteTime || '—' }}</span>
                </span>
                <span class="route-result-stat">
                  <span class="stat-label">策略</span>
                  <span class="stat-value stat-tag">{{ routeSummaryStrategy }}</span>
                </span>
                <span class="route-result-stat">
                  <span class="stat-label">方式</span>
                  <span class="stat-value stat-tag">{{ routeSummaryMode }}</span>
                </span>
              </div>
              <div class="route-result-actions">
                <el-button size="small" :icon="Close" @click="clearRoute">清除路线</el-button>
                <el-button size="small" :icon="RefreshRight" @click="confirmReset" type="danger" plain>重置</el-button>
              </div>
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
              <el-radio-button value="walk">🚶 步行</el-radio-button>
              <el-radio-button value="bike">🚲 骑行</el-radio-button>
              <el-radio-button value="shuttle">🛺 摆渡</el-radio-button>
            </el-radio-group>
          </div>

          <!------ Multi-target toggle ------>
          <div class="panel-section">
            <div class="toggle-row">
              <label class="section-label">多目标模式</label>
              <el-switch v-model="isMultiTarget" active-text="多" inactive-text="单" />
            </div>
          </div>

          <!------ Plan button (always visible, tooltip when disabled) ------>
          <div class="panel-section panel-actions">
            <el-button
              type="primary"
              size="large"
              :loading="nav.loading.value"
              :disabled="!nav.startNodeId.value || !nav.endNodeIds.value.length || !nav.scenicAreaId.value"
              class="plan-btn"
              @click="handlePlanRoute"
              :title="!nav.startNodeId.value ? '请先设置起点' : !nav.endNodeIds.value.length ? '请先设置终点' : '规划游览路线'"
            >
              规划路线
            </el-button>
          </div>

          <!------ Advanced: Node list toggle ------>
          <div class="panel-section" v-if="nodes.length">
            <div class="advanced-toggle" @click="showNodeList = !showNodeList">
              <span>{{ showNodeList ? '▼' : '▶' }} {{ showNodeList ? '隐藏' : '显示' }}路网节点 (高级)</span>
              <span class="toggle-hint">共 {{ nodes.length }} 个节点</span>
            </div>
          </div>

          <!------ Node list (hidden by default, wrapped in advanced toggle) ------>
          <template v-if="showNodeList">
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
                    <span v-if="node.nodeId === nav.startNodeId.value" class="node-dot-label" aria-label="起点">起</span>
                    <span v-else-if="nav.endNodeIds.value.includes(node.nodeId)" class="node-dot-label" aria-label="终点">终</span>
                    <span class="node-name">{{ node.scenicAreaName || node.name }}</span>
                    <span v-if="node.scenicAreaName" class="node-scenic-badge">{{ node.scenicAreaName }}</span>
                    <span v-else class="node-id">#{{ node.nodeId }}</span>
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
          </template>

          <!------ Bottom action buttons (only when no route result shown) ------>
          <div class="panel-section panel-actions-bottom" v-if="!isrouteReady">
            <el-button :icon="RefreshRight" @click="confirmReset" type="danger" plain>
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
              :label="(node.scenicAreaName || node.name) + ' (#' + node.nodeId + ')'"
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
                <span role="img" :aria-label="facilityTypeMap[f.type]?.ariaLabel || '设施图标'">{{ facilityTypeMap[f.type]?.icon || '📍' }}</span> {{ f.name }}
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
        @click="onMapClick"
      />

      <!-- ── Map click popup — only shows the closest node (multi-node search is transparent for routing) ── -->
      <Transition name="popup">
        <div v-if="nearbyClickNodes.length > 0 && nearbyClickPos" class="click-menu"
             :style="{ left: nearbyClickPos.x + 'px', top: nearbyClickPos.y + 'px' }">
          <div class="click-menu-header">
            <strong>{{ nearbyClickNodes[0].label }}</strong>
            <span class="click-menu-dist">{{ nearbyClickNodes[0].distance }}m</span>
            <button type="button" class="menu-close" @click="closeMenu">✕</button>
          </div>
          <div class="click-menu-actions">
            <button type="button" class="menu-btn menu-btn-start" @click="setStartFromNearby(nearbyClickNodes[0])">
              <span>🟢</span> 设为起点
            </button>
            <button type="button" class="menu-btn menu-btn-end" @click="setEndFromNearby(nearbyClickNodes[0])">
              <span>🟠</span> 设为终点
            </button>
          </div>
        </div>
      </Transition>

      <!-- ── Legacy marker-click context menu (kept for marker clicks) ── -->
      <Transition name="popup">
        <div v-if="clickMenuNode && clickMenuPos" class="click-menu" :style="{ left: clickMenuPos.x + 'px', top: clickMenuPos.y + 'px' }">
          <div class="click-menu-header">
            <strong>{{ clickMenuNode.scenicAreaName || clickMenuNode.name }}</strong>
            <span class="click-menu-dist">{{ clickMenuDistance }}m</span>
          </div>
          <div class="click-menu-actions">
            <button type="button" class="menu-btn menu-btn-start" @click="menuSetStart">
              <span>🟢</span> 设为起点
            </button>
            <button type="button" class="menu-btn menu-btn-end" @click="menuSetEnd">
              <span>🟠</span> 设为终点
            </button>
            <button type="button" class="menu-btn menu-btn-waypoint" @click="menuAddWaypoint">
              <span>⚪</span> {{ isMultiTarget ? '加入路径(下一站)' : '加入路径' }}
            </button>
            <button type="button" class="menu-btn menu-btn-focus" @click="menuFocusNode">
              <span>📍</span> 地图聚焦
            </button>
          </div>
          <button type="button" class="menu-close" @click="closeMenu">✕</button>
        </div>
      </Transition>

      <!-- Panel toggle button (mobile) -->
      <button
        type="button"
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
      <Transition name="shared-fade">
        <div
          v-if="popupNode && popupPixel"
          class="marker-popup"
          :style="{ left: popupPixel.x + 'px', top: popupPixel.y + 'px' }"
        >
          <div class="popup-header">
            <span class="popup-name">{{ popupNode.scenicAreaName || popupNode.name }}</span>
            <span v-if="popupNode.scenicAreaName" class="node-scenic-badge">{{ popupNode.scenicAreaName }}</span>
            <span v-else class="popup-id">#{{ popupNode.nodeId }}</span>
            <button type="button" class="popup-close" @click="closePopup">×</button>
          </div>
          <div class="popup-actions">
            <button
              type="button"
              class="popup-btn popup-btn-start"
              :disabled="popupNode.nodeId === nav.startNodeId.value"
              @click="setStartFromPopup"
            >
              <span class="popup-dot" style="background:#67C23A"></span>
              设为起点
            </button>
            <button
              type="button"
              class="popup-btn popup-btn-end"
              :disabled="nav.endNodeIds.value.includes(popupNode.nodeId) && !isMultiTarget"
              @click="setEndFromPopup"
            >
              <span class="popup-dot" style="background:var(--el-color-primary)"></span>
              设为终点
            </button>
          </div>
          <div class="popup-arrow"></div>
        </div>
      </Transition>

      <!-- ── Route info overlay on map ── -->
      <Transition name="shared-fade">
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
      <Transition name="shared-fade">
        <div v-if="isMobile && panelCollapsed && isrouteReady" class="mini-control">
          <div class="mini-info">
            <span>{{ routeSummaryMode === '步行' ? '🚶' : routeSummaryMode === '骑行' ? '🚲' : '🚍' }}</span>
            <span>{{ routeSummaryTime }} · {{ routeSummaryDistance }}</span>
          </div>
          <button type="button" class="mini-expand" @click="panelCollapsed = false">展开面板 ▲</button>
        </div>
      </Transition>

      <!-- ── Mobile FAB (floating action button) ── -->
      <div v-if="isMobile && nodes.length" class="mobile-fab-container">
        <Transition name="shared-fade">
          <div v-if="fabExpanded" class="fab-actions">
            <button type="button" class="fab-action fab-action-plan" @click="fabPlanRoute">规划</button>
            <button type="button" class="fab-action fab-action-clear" @click="fabClearRoute">清除</button>
            <button type="button" class="fab-action fab-action-reset" @click="fabReset">重置</button>
          </div>
        </Transition>
        <button type="button" class="fab-btn" @click="toggleFab" :class="{ active: fabExpanded }">
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

.welcome-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin: 10px 0 0;
}

.session-hint {
  color: var(--el-color-primary);
  font-weight: 500;
}

/* ── Example chips (welcome card) ── */
.example-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 14px;
}

.example-chip {
  font-size: 12px;
  color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.08);
  padding: 4px 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
  user-select: none;
}

.example-chip:hover {
  background: rgba(64, 158, 255, 0.18);
  transform: scale(1.04);
}

.example-chip:active {
  transform: scale(0.96);
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

.scenic-search-input {
  width: 100%;
}

.scenic-switch-input {
  width: 140px;
  flex-shrink: 0;
}

.full-width {
  width: 100%;
}

/* ── Current scenic area info bar ── */
.current-scenic-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  flex-wrap: wrap;
}

.current-scenic-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary, #2c3e50);
  white-space: nowrap;
}

.current-scenic-count {
  font-size: 12px;
  color: var(--el-text-color-secondary, #7f8c8d);
  white-space: nowrap;
  margin-right: auto;
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
   FROM→TO SELECTION CARDS
   ═══════════════════════════════════════════════ */
.fromto-section {
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fromto-card {
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
  padding: 10px 12px;
  transition: border-color 0.2s;
}

.fromto-card.start-card {
  border-left: 3px solid var(--el-color-success, #67C23A);
}

.fromto-card.end-card {
  border-left: 3px solid var(--el-color-primary);
}

.fromto-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin-bottom: 4px;
}

.fromto-value {
  display: flex;
  align-items: center;
  gap: 6px;
}

.fromto-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #2c3e50);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.fromto-clear {
  flex-shrink: 0;
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-size: 14px;
}

.fromto-card .el-autocomplete {
  width: 100%;
}

.fromto-swap {
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  cursor: pointer;
  padding: 2px 0;
  user-select: none;
  transition: opacity 0.2s;
}

.fromto-swap:hover {
  opacity: 0.7;
}

.extra-ends {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.extra-end-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  background: rgba(64, 158, 255, 0.08);
  color: var(--el-color-primary);
  padding: 2px 8px;
  border-radius: 10px;
}

/* ═══════════════════════════════════════════════
   ROUTE RESULT CARD (prominent, after planning)
   ═══════════════════════════════════════════════ */
.route-result-card {
  margin: 4px 16px 8px;
  border: 1px solid var(--el-color-success, #67C23A);
  border-radius: 10px;
  overflow: hidden;
  background: rgba(103, 194, 58, 0.04);
}

.route-result-header {
  background: var(--el-color-success, #67C23A);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 14px;
}

.route-result-body {
  padding: 10px 14px 12px;
}

.route-result-path {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #2c3e50);
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.route-result-stat {
  flex: 1;
  min-width: 60px;
}

.stat-label {
  display: block;
  font-size: 10px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin-bottom: 1px;
}

.stat-value {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.stat-tag {
  font-size: 11px !important;
  font-weight: 600 !important;
  color: var(--el-text-color-secondary, #7f8c8d) !important;
}

.route-result-actions {
  display: flex;
  gap: 8px;
}

.route-result-actions .el-button {
  font-size: 12px;
}

/* ═══════════════════════════════════════════════
   ADVANCED TOGGLE (show/hide node list)
   ═══════════════════════════════════════════════ */
.advanced-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-color-primary);
  cursor: pointer;
  user-select: none;
  transition: opacity 0.2s;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.advanced-toggle:hover {
  opacity: 0.7;
}

.toggle-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-weight: 400;
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

.node-dot-label {
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
  flex-shrink: 0;
  color: var(--el-text-color-secondary, #909399);
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

.node-scenic-badge {
  font-size: 11px;
  font-weight: 600;
  color: #0D9488;
  background: rgba(13, 148, 136, 0.1);
  padding: 1px 8px;
  border-radius: 10px;
  flex-shrink: 0;
  white-space: nowrap;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
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
  color: var(--el-color-success);
}

.badge-end {
  background: rgba(255, 107, 53, 0.15);
  color: var(--el-color-primary);
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
   NODE SEARCH RESULTS (API-backed)
   ═══════════════════════════════════════════════ */
.search-results {
  margin-top: 8px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 6px;
  max-height: 240px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  transition: background 0.15s;
}

.search-result-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.search-result-item:last-child {
  border-bottom: none;
}

.result-main {
  flex: 1;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.result-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background 0.15s;
}

.result-btn:hover { background: var(--el-fill-color-light, #f5f7fa); }

.result-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.result-type {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  background: var(--el-fill-color-lighter, #f0f2f5);
  padding: 2px 8px;
  border-radius: 4px;
}

.search-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  padding: 16px;
  text-align: center;
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
  color: var(--el-color-primary);
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
  color: var(--el-color-primary);
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
  color: var(--el-color-warning);
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
  border-color: var(--el-color-success);
}

.popup-btn-end:hover:not(:disabled) {
  background: rgba(255, 107, 53, 0.08);
  border-color: var(--el-color-primary);
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

/* shared fade transition */
.shared-fade-enter-active,
.shared-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.shared-fade-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
.shared-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
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
  color: var(--el-color-primary);
}

.overlay-strategy {
  font-size: 11px;
  color: var(--el-text-color-secondary, #7f8c8d);
  margin-top: 1px;
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
  background: var(--el-color-primary);
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
  background: var(--el-color-primary);
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
  background: var(--el-color-primary);
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
   CLICK MENU (map click context menu)
   ═══════════════════════════════════════════════ */
.click-menu {
  position: absolute;
  z-index: 999;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 8px;
  min-width: 160px;
  transform: translate(-50%, -100%);
  margin-top: -12px;
  pointer-events: auto;
}

.click-menu-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  margin-bottom: 4px;
}

.click-menu-header strong {
  font-size: 13px;
}

.click-menu-dist {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
}

/* ── Multi-node click menu ── */
.click-menu-multi {
  min-width: 220px;
  max-width: 280px;
  max-height: 340px;
  display: flex;
  flex-direction: column;
}

.click-menu-multi .click-menu-header {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.click-menu-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
  max-height: 280px;
}

.click-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
  transition: background 0.15s;
}

.click-menu-item:last-child {
  border-bottom: none;
}

.click-menu-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.cmi-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  min-width: 0;
}

.cmi-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

.cmi-label.is-scenic {
  color: #00897B;
}

.cmi-label.is-poi {
  color: #388E3C;
}

.cmi-distance {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.cmi-type {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--el-fill-color-lighter, #f0f2f5);
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.cmi-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-left: 4px;
}

.cmi-actions .menu-btn {
  font-size: 14px;
  padding: 2px 4px;
  min-width: 24px;
  justify-content: center;
}

.click-menu-multi .menu-close {
  position: static;
  font-size: 14px;
  padding: 0 4px;
}

.click-menu-header .menu-close {
  position: static;
}
/* ── end multi-node ── */

.click-menu-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 4px;
  font-size: 13px;
  text-align: left;
  transition: background 0.15s;
}

.menu-btn:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.menu-btn-start {
  color: var(--el-color-success, #67C23A);
}

.menu-btn-end {
  color: var(--el-color-primary);
}

.menu-btn-waypoint {
  color: var(--el-text-color-primary, #303133);
}

.menu-btn-focus {
  color: var(--el-color-info, #3498DB);
}

.menu-close {
  position: absolute;
  top: 4px;
  right: 4px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
  padding: 2px 6px;
  border-radius: 4px;
}

.menu-close:hover {
  background: var(--el-fill-color-lighter, #f0f2f5);
}

/* Popup transition (shared by marker popup & click menu) */
.popup-enter-active {
  transition: all 0.2s ease-out;
}

.popup-leave-active {
  transition: all 0.15s ease-in;
}

.popup-enter-from {
  opacity: 0;
  transform: translate(-50%, -90%) scale(0.9);
}

.popup-leave-to {
  opacity: 0;
  transform: translate(-50%, -90%) scale(0.9);
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
