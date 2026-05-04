export interface PathNode {
  nodeId: number
  name: string
  sequence: number
  latitude: number
  longitude: number
  action: 'start' | 'visit' | 'end'
  arrivalTime: string | null
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
  level: number
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
