import request from '@/api/request'
import type { UserInfo, UserPreferences } from '@/types/user'

export const userApi = {
  getInfo: () => request.get<UserInfo>('/api/user/info'),
  updateInfo: (data: Partial<UserInfo>) => request.put<UserInfo>('/api/user/info', data),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put<null>('/api/user/password', data),
  getPreferences: () => request.get<UserPreferences>('/api/user/preferences'),
  updatePreferences: (data: Partial<UserPreferences>) =>
    request.put<UserPreferences>('/api/user/preferences', data),
}
