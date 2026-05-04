import request from '@/api/request'
import type { LoginResponse, LoginRequest, RegisterRequest } from '@/types/auth'

export const authApi = {
  login: (data: LoginRequest) =>
    request.post<LoginResponse>('/api/auth/login', data),
  register: (data: RegisterRequest) =>
    request.post<{ userId: number }>('/api/auth/register', data),
  refresh: (refreshToken: string) =>
    request.post<LoginResponse>('/api/auth/refresh', { refreshToken }),
  logout: () =>
    request.post<null>('/api/auth/logout'),
}
