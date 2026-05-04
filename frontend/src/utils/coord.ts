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
