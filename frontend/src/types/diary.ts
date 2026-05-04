export interface DiaryItem {
  id: string
  userId: number
  userNickname: string
  title: string
  summary: string
  coverImage: string | null
  tags: string[]
  rating: number
  likeCount: number
  viewCount: number
  commentCount: number
  createdAt: string
}

export interface DiaryCreateParams {
  title: string
  content: string
  scenicAreaId: number[]
  tags: string[]
  rating: number
  status: number
  mood: string
  weather: string
  images: string[]
}

export interface Comment {
  id: string
  userId: number
  userNickname: string
  content: string
  likeCount: number
  replyCount: number
  createdAt: string
}
