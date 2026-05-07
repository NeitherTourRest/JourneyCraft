<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useAmap } from '@/composables/useAmap'

interface Props {
  center?: [number, number]
  zoom?: number
}

const props = withDefaults(defineProps<Props>(), {
  center: () => [116.397, 39.916],
  zoom: 16,
})

const emit = defineEmits<{
  ready: [map: any]
  click: [lnglat: [number, number]]
}>()

const { loadAmap } = useAmap()
const containerRef = ref<HTMLDivElement>()
let map: any = null
let amapModule: any = null  // Store AMap module ref for exposed methods
const markers: any[] = []
const polylines: any[] = []
const loadFailed = ref(false)
const loadErrorMsg = ref('')
const isLoading = ref(false)

onMounted(async () => {
  isLoading.value = true
  try {
    const AMap = await loadAmap()
    if (!AMap) {
      loadFailed.value = true
      loadErrorMsg.value = '高德地图 Key 未配置或加载失败，请检查 .env 文件'
      return
    }
    if (!containerRef.value) {
      loadFailed.value = true
      loadErrorMsg.value = '地图容器未找到'
      return
    }

    // ★ 保存 AMap 模块引用给暴露的方法使用
    amapModule = AMap

    // ★ 先让容器可见，再创建地图实例（避免 display:none 导致 0x0 尺寸）
    isLoading.value = false
    await nextTick()

    map = new AMap.Map(containerRef.value, {
      zoom: props.zoom,
      center: props.center,
      viewMode: '2D',
    })

    console.log('[AmapContainer] 地图实例创建成功, 中心:', props.center, '容器尺寸:', containerRef.value?.clientWidth, 'x', containerRef.value?.clientHeight)

    map.on('complete', () => {
      console.log('[AmapContainer] 地图瓦片加载完成')
    })

    map.on('click', (e: any) => {
      emit('click', [e.lnglat.getLng(), e.lnglat.getLat()])
    })

    emit('ready', map)
  } catch (err: any) {
    console.error('[AmapContainer] 地图加载失败:', err)
    loadFailed.value = true
    isLoading.value = false
    loadErrorMsg.value = err?.message || '地图加载失败，请检查控制台详细错误'
  }
})

onUnmounted(() => {
  clearOverlays()
  if (map) {
    map.destroy()
    map = null
  }
  markers.length = 0
  polylines.length = 0
  amapModule = null
})

// ── Exposed Methods ──

function addMarker(
  lng: number,
  lat: number,
  options?: {
    content?: string
    color?: string
    draggable?: boolean
    label?: string
  },
): any {
  if (!map || !amapModule) return null
  const marker = new amapModule.Marker({
    position: [lng, lat],
    content: options?.content || '',
    label: options?.label ? { content: options.label } : undefined,
    draggable: options?.draggable || false,
  })
  map.add(marker)
  markers.push(marker)
  return marker
}

function addTextMarker(
  lng: number,
  lat: number,
  text: string,
  color?: string,
  onClick?: (pos: { lng: number; lat: number }) => void,
) {
  const marker = addMarker(lng, lat, {
    content: `<div style="background:${color || '#3366FF'};color:white;padding:2px 6px;border-radius:4px;font-size:12px;white-space:nowrap;cursor:pointer">${text}</div>`,
  })
  if (onClick && marker) {
    marker.on('click', () => onClick({ lng, lat }))
  }
  return marker
}

function drawPolyline(
  path: [number, number][],
  options?: {
    strokeColor?: string
    strokeWeight?: number
    showDir?: boolean
  },
): any {
  if (!map || !amapModule) return null
  const polyline = new amapModule.Polyline({
    path,
    strokeColor: options?.strokeColor || '#3366FF',
    strokeWeight: options?.strokeWeight || 6,
    strokeOpacity: 0.8,
    showDir: options?.showDir || false,
    dirColor: '#FFFFFF',
  })
  map.add(polyline)
  polylines.push(polyline)
  return polyline
}

function clearOverlays() {
  if (map) {
    try {
      map.remove([...markers, ...polylines])
    } catch (err) {
      console.error('[AmapContainer] clearOverlays failed:', err)
    }
  }
  markers.length = 0
  polylines.length = 0
}

function setFitView(overlays?: any[]) {
  if (!map) return
  if (overlays && overlays.length > 0) {
    map.setFitView(overlays)
  } else {
    map.setFitView()
  }
}

function getMap() {
  return map
}

function getAMap() {
  return amapModule
}

function lngLatToPixel(lng: number, lat: number): { x: number; y: number } | null {
  if (!map || !amapModule) return null
  const px = map.lngLatToContainer(new amapModule.LngLat(lng, lat))
  if (!px) return null
  return { x: Math.round(px.x), y: Math.round(px.y) }
}

defineExpose({
  addMarker,
  addTextMarker,
  drawPolyline,
  clearOverlays,
  setFitView,
  getMap,
  getAMap,
  lngLatToPixel,
})
</script>

<template>
  <div class="amap-wrapper" :style="{ minHeight: '400px', height: '100%', width: '100%', position: 'relative' }">
    <!-- Loading state -->
    <div v-if="isLoading" class="amap-status loading">
      <div class="spinner"></div>
      <span>地图加载中...</span>
    </div>
    <!-- Error state -->
    <div v-else-if="loadFailed" class="amap-status error">
      <span class="error-icon">⚠️</span>
      <span class="error-text">{{ loadErrorMsg }}</span>
      <p class="error-hint">请检查浏览器控制台 (F12) 查看详细错误</p>
    </div>
    <!-- Map container -->
    <div ref="containerRef" class="amap-container" :style="{ display: loadFailed || isLoading ? 'none' : 'block' }"></div>
  </div>
</template>

<style scoped>
.amap-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
}

.amap-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}

.amap-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 400px;
  gap: 12px;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
}

.amap-status.error {
  color: var(--el-color-danger, #E74C3C);
}

.amap-status .error-icon {
  font-size: 48px;
}

.amap-status .error-text {
  font-size: 16px;
  font-weight: 500;
}

.amap-status .error-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin: 0;
}

.is-loading {
  animation: spin 1s linear infinite;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--el-border-color, #dcdfe6);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
