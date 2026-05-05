import request from '@/api/request'
import type { UserInfo, UserPreferences } from '@/types/user'

export const userApi = {
  getInfo: (userId: number) =>
    request.get<UserInfo>('/api/user/info', { params: { userId } }),

  updateInfo: (userId: number, data: Partial<UserInfo>) =>
    request.put<UserInfo>('/api/user/info', data, { params: { userId } }),

  changePassword: (userId: number, data: { oldPassword: string; newPassword: string }) =>
    request.put<null>('/api/user/password', data, { params: { userId } }),

  getPreferences: (userId: number) =>
    request.get<UserPreferences>('/api/user/preferences', { params: { userId } }),

  updatePreferences: (userId: number, data: Partial<UserPreferences>) =>
    request.put<UserPreferences>('/api/user/preferences', data, { params: { userId } }),
}
