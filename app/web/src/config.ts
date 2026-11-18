/**
 * 後端 API 位址。建置時由環境變數 `VITE_API_BASE_URL` 決定；
 * 沒設定時是空字串，請求走相對路徑（與後端同源部署，或開發時由開發伺服器代理）。
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';
