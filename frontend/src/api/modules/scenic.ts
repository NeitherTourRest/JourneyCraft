import request from '@/api/request'
import type { PageResult, PageParams } from '@/types/api'
import type { ScenicItem, Building, Facility } from '@/types/scenic'

export const scenicApi = {
  getList: (params: PageParams & { type?: number; city?: string; sortBy?: string; sortOrder?: string }) =>
    request.get<PageResult<ScenicItem>>('/api/scenic/list', { params }),
  search: (params: { keyword: string; type?: number; city?: string } & PageParams) =>
    request.get<PageResult<ScenicItem>>('/api/scenic/search', { params }),
  getById: (id: number) => request.get<ScenicItem>(`/api/scenic/${id}`),
  getBuildings: (id: number, type?: number) =>
    request.get<Building[]>(`/api/scenic/${id}/buildings`, { params: { type } }),
  getFacilities: (id: number, params?: { type?: number; buildingId?: number }) =>
    request.get<Facility[]>(`/api/scenic/${id}/facilities`, { params }),
}
