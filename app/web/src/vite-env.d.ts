/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 後端 API 位址，建置時決定；沒設定時是空字串（相對路徑，同源部署）。 */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
