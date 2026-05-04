import { request } from '@/api/request'
import type { RouteResult, MultiRouteResult, CongestionData, NearbyFacility, PhotoSpot } from '@/types/navigation'

export const navigationApi = {
  planRoute: (params: { scenicAreaId: number; startNodeId: number; endNodeId: number; strategy?: string; transportMode?: string; algorithm?: string }) =>
    request.post<RouteResult>('/api/navigation/route', new URLSearchParams(params as any).toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    }),
  planMultiRoute: (params: { scenicAreaId: number; startNodeId: number; endNodeIds: string; needReturn?: boolean; strategy?: string; transportMode?: string }) =>
    request.post<MultiRouteResult>('/api/navigation/multi-route', new URLSearchParams(params as any).toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    }),
  getCongestion: (scenicId: number) =>
    request.get<CongestionData>(`/api/navigation/congestion/${scenicId}`),
  getNearbyFacilities: (params: { scenicAreaId: number; nodeId: number; type?: number; radius?: number; limit?: number }) =>
    request.get<NearbyFacility[]>('/api/navigation/facilities/nearby', { params }),
  getPhotoSpots: (scenicAreaId: number) =>
    request.get<PhotoSpot[]>(`/api/navigation/photo-spots/scenic/${scenicAreaId}`),
}
