export interface UserPreferences {
  interests: string[]
  transportType: string
  foodPreferences: string[]
  maxWalkDistance: number
  budgetPerDay: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatarUrl: string | null
  phone: string | null
  email: string | null
  preferences: UserPreferences
  status: number
}
