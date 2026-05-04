import { describe, it, expect } from 'vitest'
import { wgs84ToGcj02, pathNodesToGcj02 } from '../coord'
import type { PathNode } from '@/types/navigation'

describe('wgs84ToGcj02', () => {
  it('converts 故宫午门 WGS-84 to GCJ-02 within tolerance', () => {
    const [lng, lat] = wgs84ToGcj02(116.397, 39.9161)
    // Expected from coordtransform: [116.403244, 39.917503]
    expect(Math.abs(lng - 116.403244)).toBeLessThan(0.001)
    expect(Math.abs(lat - 39.917503)).toBeLessThan(0.001)
  })

  it('returns [number, number] tuple', () => {
    const result = wgs84ToGcj02(120.0, 30.0)
    expect(result).toHaveLength(2)
    expect(typeof result[0]).toBe('number')
    expect(typeof result[1]).toBe('number')
  })

  it('handles negative coordinates (southern/western hemisphere)', () => {
    const result = wgs84ToGcj02(-74.006, -33.9249)
    expect(result).toHaveLength(2)
    expect(typeof result[0]).toBe('number')
    expect(typeof result[1]).toBe('number')
  })
})

describe('pathNodesToGcj02', () => {
  it('converts array of PathNodes to GCJ-02 coordinate pairs', () => {
    const nodes: PathNode[] = [
      { nodeId: 1, name: 'A', sequence: 0, latitude: 39.9161, longitude: 116.397, action: 'start', arrivalTime: null },
      { nodeId: 2, name: 'B', sequence: 1, latitude: 39.9162, longitude: 116.3971, action: 'end', arrivalTime: null },
    ]
    const result = pathNodesToGcj02(nodes)
    expect(result).toHaveLength(2)
    expect(result[0]).toHaveLength(2)
    expect(result[1]).toHaveLength(2)
  })

  it('returns empty array for empty input', () => {
    const result = pathNodesToGcj02([])
    expect(result).toEqual([])
  })

  it('handles single node', () => {
    const nodes: PathNode[] = [
      { nodeId: 1, name: 'Solo', sequence: 0, latitude: 30.0, longitude: 120.0, action: 'start', arrivalTime: null },
    ]
    const result = pathNodesToGcj02(nodes)
    expect(result).toHaveLength(1)
  })
})
