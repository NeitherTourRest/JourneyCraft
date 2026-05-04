import request from '@/api/request'
import type { PageResult, PageParams } from '@/types/api'

export const favoriteApi = {
  add: (data: { type: number; targetId: number }) => request.post<null>('/api/favorite', data),
  remove: (id: number) => request.delete<null>(`/api/favorite/${id}`),
  getList: (params: PageParams & { type?: number }) =>
    request.get<PageResult<any>>('/api/favorite/list', { params }),
  batchRemove: (data: { ids: number[] }) =>
    request.post<null>('/api/favorite/batch', data),
  createCollection: (data: { name: string; description?: string }) =>
    request.post<{ id: number }>('/api/favorite/collection', data),
  getCollections: () => request.get<any[]>('/api/favorite/collections'),
}
