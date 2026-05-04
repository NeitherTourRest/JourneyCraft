import AMapLoader from '@amap/amap-jsapi-loader'

let amapPromise: Promise<any> | null = null

export function useAmap() {
  /**
   * 加载高德地图 JS API（单例模式）。
   * 首次调用加载 SDK, 后续调用返回同一个 Promise。
   * Key 未配置时返回 null 并在控制台警告。
   */
  function loadAmap(): Promise<any> | null {
    if (amapPromise) return amapPromise

    const key = import.meta.env.VITE_AMAP_KEY
    if (!key) {
      console.warn('[useAmap] 高德地图 Key 未配置, 请设置 VITE_AMAP_KEY')
      return null
    }

    // ★ 高德 JS API 2.0 安全密钥：必须在 SDK 加载前设置
    const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE
    if (securityCode) {
      ;(window as any)._AMapSecurityConfig = {
        securityJsCode: securityCode,
      }
    }

    amapPromise = AMapLoader.load({
      key,
      version: '2.0',
    })

    return amapPromise
  }

  return { loadAmap }
}
