export interface LoginRequest {
  username: string
  password: string
}

export interface TokenInfo {
  token: string
  refreshToken: string
  expiresIn: number
}

export interface LoginResponse extends TokenInfo {
  user: UserBrief
}

export interface UserBrief {
  id: number
  username: string
  nickname: string
  avatarUrl: string | null
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
  phone?: string
  email?: string
}
