<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
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
const markers: any[] = []
const polylines: any[] = []

onMounted(async () => {
  const AMap = await loadAmap()
  if (!AMap || !containerRef.value) return

  map = new AMap.Map(containerRef.value, {
    zoom: props.zoom,
    center: props.center,
    viewMode: '2D',
  })

  map.on('click', (e: any) => {
    emit('click', [e.lnglat.getLng(), e.lnglat.getLat()])
  })

  emit('ready', map)
})

onUnmounted(() => {
  clearOverlays()
  if (map) {
    map.destroy()
    map = null
  }
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
  if (!map) return null
  const marker = new (window as any).AMap.Marker({
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
) {
  return addMarker(lng, lat, {
    content: `<div style="background:${color || '#3366FF'};color:white;padding:2px 6px;border-radius:4px;font-size:12px;white-space:nowrap">${text}</div>`,
  })
}

function drawPolyline(
  path: [number, number][],
  options?: {
    strokeColor?: string
    strokeWeight?: number
    showDir?: boolean
  },
): any {
  if (!map) return null
  const polyline = new (window as any).AMap.Polyline({
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
    map.remove([...markers, ...polylines])
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

defineExpose({
  addMarker,
  addTextMarker,
  drawPolyline,
  clearOverlays,
  setFitView,
  getMap,
})
</script>

<template>
  <div ref="containerRef" class="amap-container"></div>
</template>

<style scoped>
.amap-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>
