export interface ScenicItem {
  id: number
  name: string
  type: 0 | 1
  city: string
  address: string
  latitude: number
  longitude: number
  description: string
  rating: number
  heatScore: number
  visitCount: number
  ticketPrice: number
  openingHours: object
  images: string[]
}

export interface Building {
  id: number
  name: string
  type: number
  floorCount: number
  latitude: number
  longitude: number
  description: string
  images: string[]
}

export interface Facility {
  id: number
  name: string
  type: number
  subtype: string | null
  latitude: number
  longitude: number
  rating: number
  priceRange: string | null
  images: string[]
}
