import { request } from '@/api/request'
import type { PageResult, PageParams } from '@/types/api'

export const historyApi = {
  getViewHistory: (params: PageParams & { type?: number }) =>
    request.get<PageResult<any>>('/api/history/view', { params }),
  getSearchHistory: (params: { type?: number; limit?: number }) =>
    request.get<any[]>('/api/history/search', { params }),
  getNavHistory: (params: { scenicAreaId?: number }) =>
    request.get<any[]>('/api/history/navigation', { params }),
  recordView: (data: { type: number; targetId: number }) =>
    request.post<null>('/api/history/view', data),
  clear: () => request.delete<null>('/api/history/clear'),
}
