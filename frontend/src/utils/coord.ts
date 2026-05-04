import coordtransform from 'coordtransform'
import type { PathNode } from '@/types/navigation'

/**
 * Convert a single WGS-84 coordinate to GCJ-02.
 * GCJ-02 is the Chinese coordinate system used by Amap and other domestic map services.
 */
export function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  const [gcjLng, gcjLat] = coordtransform.wgs84togcj02(lng, lat)
  return [gcjLng, gcjLat]
}

/**
 * Convert an array of PathNode objects to GCJ-02 coordinate pairs.
 */
export function pathNodesToGcj02(nodes: PathNode[]): Array<[number, number]> {
  return nodes.map(node => {
    const [gcjLng, gcjLat] = coordtransform.wgs84togcj02(node.longitude, node.latitude)
    return [gcjLng, gcjLat]
  })
}

/**
 * GCJ-02 → WGS-84 反向转换（高德坐标转 WGS-84）
 */
export function gcj02ToWgs84(lng: number, lat: number): [number, number] {
  const [wgsLng, wgsLat] = coordtransform.gcj02towgs84(lng, lat)
  return [wgsLng, wgsLat]
}

/**
 * 从节点列表中查找离指定 WGS-84 坐标最近的节点。
 * 使用平面近似公式计算距离（适用于景区范围 <10km）。
 * @returns 最近节点和距离（米），超出 maxDistance 返回 null
 */
export function findNearestNode(
  wgsLng: number,
  wgsLat: number,
  nodes: PathNode[],
  maxDistance: number = 200,
): { node: PathNode; distance: number } | null {
  let nearest: PathNode | null = null
  let minDist = Infinity

  const avgLat = (wgsLat * Math.PI) / 180
  const cosLat = Math.cos(avgLat)

  for (const node of nodes) {
    const dx = (node.longitude - wgsLng) * 111320 * cosLat
    const dy = (node.latitude - wgsLat) * 111320
    const dist = Math.sqrt(dx * dx + dy * dy)

    if (dist < minDist) {
      minDist = dist
      nearest = node
    }
  }

  if (nearest && minDist <= maxDistance) {
    return { node: nearest, distance: Math.round(minDist) }
  }
  return null
}
