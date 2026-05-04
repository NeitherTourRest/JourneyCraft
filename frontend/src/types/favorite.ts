/**
 * Favorite type constants.
 * Scenic=0, Campus=1, Building=2, Facility=3, Diary=4, Route=5
 */
export const FavoriteType = {
  Scenic: 0,
  Campus: 1,
  Building: 2,
  Facility: 3,
  Diary: 4,
  Route: 5,
} as const

export type FavoriteType = (typeof FavoriteType)[keyof typeof FavoriteType]
