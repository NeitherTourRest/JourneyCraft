/// <reference types="vite/client" />

declare module 'coordtransform' {
  const coordtransform: any
  export default coordtransform
}

interface ImportMetaEnv {
  readonly VITE_AMAP_KEY: string
  readonly VITE_API_BASE_URL: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
