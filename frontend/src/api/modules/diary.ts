import request from '@/api/request'
import type { PageResult, PageParams } from '@/types/api'
import type { DiaryItem, DiaryCreateParams, Comment } from '@/types/diary'

export const diaryApi = {
  getList: (params: PageParams & { tags?: string; sortBy?: string }) =>
    request.get<PageResult<DiaryItem>>('/api/diary/list', { params }),
  getById: (id: string) => request.get<DiaryItem>(`/api/diary/${id}`),
  create: (data: DiaryCreateParams) => request.post<{ diaryId: string }>('/api/diary', data),
  update: (id: string, data: Partial<DiaryCreateParams>) => request.put<DiaryItem>(`/api/diary/${id}`, data),
  delete: (id: string) => request.delete<null>(`/api/diary/${id}`),
  like: (id: string) => request.post<{ likeCount: number; isLiked: boolean }>(`/api/diary/${id}/like`),
  comment: (id: string, data: { content: string; parentId?: string }) =>
    request.post<{ commentId: string }>(`/api/diary/${id}/comment`, data),
  getComments: (id: string, params?: PageParams) =>
    request.get<PageResult<Comment>>(`/api/diary/${id}/comments`, { params }),
}
