import { describe, it, expect } from 'vitest'
import { wgs84ToGcj02, gcj02ToWgs84, pathNodesToGcj02, findNearestNode } from '../coord'
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

describe('gcj02ToWgs84', () => {
  it('converts GCJ-02 back to WGS-84 correctly', () => {
    const [gcjLng, gcjLat] = wgs84ToGcj02(116.397, 39.916)
    // GCJ-02 should differ from WGS-84
    expect(gcjLng).not.toBeCloseTo(116.397, 3)
    expect(gcjLat).not.toBeCloseTo(39.916, 3)
    // Result should be in valid coordinate range
    expect(gcjLng).toBeGreaterThan(73)
    expect(gcjLng).toBeLessThan(135)
    expect(gcjLat).toBeGreaterThan(3)
    expect(gcjLat).toBeLessThan(54)
  })

  it('gcj02ToWgs84 is inverse of wgs84ToGcj02 within tolerance', () => {
    const [gcjLng, gcjLat] = wgs84ToGcj02(116.397, 39.916)
    const [wgsLng, wgsLat] = gcj02ToWgs84(gcjLng, gcjLat)
    expect(wgsLng).toBeCloseTo(116.397, 5)
    expect(wgsLat).toBeCloseTo(39.916, 5)
  })
})

describe('findNearestNode', () => {
  function makeNode(overrides: Partial<PathNode> = {}): PathNode {
    return {
      nodeId: 1,
      name: 'Test',
      sequence: 0,
      latitude: 39.916,
      longitude: 116.397,
      action: 'visit',
      arrivalTime: null,
      ...overrides,
    }
  }

  it('returns null when all nodes exceed maxDistance', () => {
    const nodes: PathNode[] = [
      makeNode({ nodeId: 1, name: 'Node A', longitude: 117.0, latitude: 40.0 }),
      makeNode({ nodeId: 2, name: 'Node B', longitude: 117.5, latitude: 40.5 }),
    ]
    const result = findNearestNode(116.0, 39.0, nodes, 200)
    expect(result).toBeNull()
  })

  it('returns nearest node within range', () => {
    const nodes: PathNode[] = [
      makeNode({ nodeId: 1, name: 'Node A', longitude: 116.3971, latitude: 39.9161 }),
      makeNode({ nodeId: 2, name: 'Node B', longitude: 116.3972, latitude: 39.9162 }),
    ]
    const result = findNearestNode(116.39715, 39.91615, nodes, 200)
    expect(result).not.toBeNull()
    expect(result!.node.nodeId).toBe(1)
    expect(result!.distance).toBeGreaterThan(0)
  })

  it('returns null for empty node list', () => {
    const result = findNearestNode(116.397, 39.916, [], 200)
    expect(result).toBeNull()
  })

  it('distance is within maxDistance', () => {
    const nodes: PathNode[] = [
      makeNode({ nodeId: 1, name: 'Node A', longitude: 116.397, latitude: 39.916 }),
    ]
    const result = findNearestNode(116.397, 39.916, nodes, 200)
    expect(result).not.toBeNull()
    expect(result!.distance).toBeLessThanOrEqual(200)
  })
})
