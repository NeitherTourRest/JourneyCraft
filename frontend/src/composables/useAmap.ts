import AMapLoader from '@amap/amap-jsapi-loader'

let amapPromise: Promise<any> | null = null
let loadError: Error | null = null

export function useAmap() {
  function loadAmap(): Promise<any> | null {
    // If previously failed, return null immediately (don't retry)
    if (loadError) {
      console.error('[useAmap] 地图加载已失败:', loadError.message)
      return null
    }
    if (amapPromise) return amapPromise

    const key = import.meta.env.VITE_AMAP_KEY
    if (!key) {
      console.error('[useAmap] VITE_AMAP_KEY 未设置，请在 .env 中配置高德地图 Key')
      return null
    }

    const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE
    console.log('[useAmap] 开始加载高德地图 SDK...', { key: key.slice(0, 6) + '...', hasSecurityCode: !!securityCode })

    // ★ 高德 JS API 2.0 安全密钥：必须在 SDK 加载前设置
    if (securityCode) {
      ;(window as any)._AMapSecurityConfig = {
        securityJsCode: securityCode,
      }
      console.log('[useAmap] 安全密钥已配置')
    } else {
      console.warn('[useAmap] VITE_AMAP_SECURITY_CODE 未设置，地图可能无法加载（JS API 2.0 要求）')
    }

    amapPromise = AMapLoader.load({
      key,
      version: '2.0',
    })
      .then((AMap: any) => {
        console.log('[useAmap] ✅ 高德地图 SDK 加载成功')
        return AMap
      })
      .catch((err: Error) => {
        console.error('[useAmap] ❌ 高德地图 SDK 加载失败:', err.message || err)
        loadError = err
        amapPromise = null // Reset so user can retry after fixing config
        throw err
      })

    return amapPromise
  }

  return { loadAmap }
}
