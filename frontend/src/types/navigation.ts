export interface PathNode {
  nodeId: number
  name: string
  sequence: number
  latitude: number
  longitude: number
  action: 'start' | 'visit' | 'end'  // start=起点, visit=途经, end=终点 — matches the start/end nodes state in useNavigation
  arrivalTime: string | null
  scenicAreaName?: string  // 节点所属景区名称，用于显示回退
  scenicAreaId?: number    // 节点所属景区 ID
  isPrimary?: boolean      // 是否该景区的最佳/代表节点（地图上只显示该节点）
}

export interface RouteResult {
  routeId: number
  totalDistance: number
  estimatedTime: number
  transportMode: string
  strategy: string
  nodes: PathNode[]
}

export interface MultiRouteResult {
  totalDistance: number
  totalTime: number
  visitOrder: number[]
  returnedToStart: boolean
  segments: RouteResult[]
}

export interface NodeCongestion {
  nodeId: number
  level: 0 | 1 | 2 | 3
  crowdCount: number
  color: 'green' | 'yellow' | 'red'
}

export interface CongestionData {
  scenicAreaId: number
  overallLevel: number
  updateTime: string
  nodes: NodeCongestion[]
}

export interface NearbyFacility {
  id: number
  name: string
  type: number
  latitude: number
  longitude: number
  distance: number
}

export interface PhotoSpot {
  id: number
  scenicAreaId: number
  name: string
  targetName: string
  description: string
  recommendedAngle: string
  bestTime: string
  latitude: number
  longitude: number
  rating: number
  checkInCount: number
}
