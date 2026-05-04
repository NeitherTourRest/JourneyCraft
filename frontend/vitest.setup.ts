// Vitest setup — mock CSS imports (Element Plus CSS can't be processed in jsdom)
import { vi } from 'vitest'

vi.mock('element-plus', () => {
  return {
    default: {
      install: () => {},
    },
  }
})
