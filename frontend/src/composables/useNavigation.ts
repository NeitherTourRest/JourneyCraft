import { ref, computed } from 'vue'
import { navigationApi } from '@/api/modules/navigation'
import { pathNodesToGcj02 } from '@/utils/coord'
import type {
  PathNode,
  RouteResult,
  MultiRouteResult,
  CongestionData,
  NearbyFacility,
  PhotoSpot,
} from '@/types/navigation'

export function useNavigation() {
  // ── State ──
  const scenicAreaId = ref<number | null>(null)
  const startNodeId = ref<number | null>(null)
  const endNodeIds = ref<number[]>([])
  const transportMode = ref<'walk' | 'bike' | 'shuttle'>('walk')
  const strategy = ref<string>('shortest_distance')
  const loading = ref(false)

  const currentRoute = ref<RouteResult | null>(null)
  const multiRoute = ref<MultiRouteResult | null>(null)
  const congestionData = ref<CongestionData | null>(null)
  const nodeMarkers = ref<PathNode[]>([])

  // ── Computed ──
  const routePath = computed(() => {
    if (!currentRoute.value) return null
    return pathNodesToGcj02(currentRoute.value.nodes)
  })

  const distanceKm = computed(() => {
    if (!currentRoute.value) return null
    return (currentRoute.value.totalDistance / 1000).toFixed(1) + ' km'
  })

  const estimatedTimeMin = computed(() => {
    if (!currentRoute.value) return null
    return Math.floor(currentRoute.value.estimatedTime / 60) + ' min'
  })

  // ── Methods ──
  async function planRoute() {
    if (!scenicAreaId.value || !startNodeId.value || endNodeIds.value.length === 0) return
    loading.value = true
    try {
      const res = await navigationApi.planRoute({
        scenicAreaId: scenicAreaId.value,
        startNodeId: startNodeId.value,
        endNodeId: endNodeIds.value[0],
        strategy: strategy.value,
        transportMode: transportMode.value,
        algorithm: 'astar',
      })
      currentRoute.value = res.data.data
      multiRoute.value = null
    } finally {
      loading.value = false
    }
  }

  async function planMultiRoute(needReturn = false) {
    if (!scenicAreaId.value || !startNodeId.value || endNodeIds.value.length < 2) return
    loading.value = true
    try {
      const res = await navigationApi.planMultiRoute({
        scenicAreaId: scenicAreaId.value,
        startNodeId: startNodeId.value,
        endNodeIds: endNodeIds.value.join(','),
        needReturn,
        strategy: strategy.value,
        transportMode: transportMode.value,
      })
      multiRoute.value = res.data.data
      currentRoute.value = null
    } finally {
      loading.value = false
    }
  }

  async function loadCongestion() {
    if (!scenicAreaId.value) return
    const res = await navigationApi.getCongestion(scenicAreaId.value)
    congestionData.value = res.data.data
  }

  async function loadNearbyFacilities(nodeId: number, type?: number): Promise<NearbyFacility[]> {
    if (!scenicAreaId.value) return []
    const res = await navigationApi.getNearbyFacilities({
      scenicAreaId: scenicAreaId.value,
      nodeId,
      type,
      radius: 500,
      limit: 10,
    })
    return res.data.data
  }

  async function loadPhotoSpots(): Promise<PhotoSpot[]> {
    if (!scenicAreaId.value) return []
    const res = await navigationApi.getPhotoSpots(scenicAreaId.value)
    return res.data.data
  }

  function addEndNode(nodeId: number) {
    endNodeIds.value.push(nodeId)
  }

  function removeEndNode(nodeId: number) {
    endNodeIds.value = endNodeIds.value.filter((id) => id !== nodeId)
  }

  function setStartNode(nodeId: number) {
    startNodeId.value = nodeId
  }

  function reset() {
    startNodeId.value = null
    endNodeIds.value = []
    currentRoute.value = null
    multiRoute.value = null
    congestionData.value = null
  }

  return {
    // state
    scenicAreaId,
    startNodeId,
    endNodeIds,
    transportMode,
    strategy,
    loading,
    currentRoute,
    multiRoute,
    congestionData,
    nodeMarkers,
    // computed
    routePath,
    distanceKm,
    estimatedTimeMin,
    // methods
    planRoute,
    planMultiRoute,
    loadCongestion,
    loadNearbyFacilities,
    loadPhotoSpots,
    addEndNode,
    removeEndNode,
    setStartNode,
    reset,
  }
}
