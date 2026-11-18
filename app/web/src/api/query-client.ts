import { QueryClient } from '@tanstack/react-query';

/**
 * 整個應用程式共用的一份後端資料快取。
 * 什麼時候重抓、失敗要不要重試，由用到資料的功能在各自的查詢上設定，這裡不放預設規則。
 */
export const queryClient = new QueryClient();

/**
 * 畫面自己定時重讀的間隔：讀取失敗時的再試，和即時訊號不可信時的定時重抓，都用這一個。
 * 畫面上告訴櫃檯「每 5 秒」，意思是資料最多落後 5 秒；每次重讀從送出到畫面換好要花一點時間，
 * 所以間隔比 5 秒短，讓那段時間也算在 5 秒之內。
 */
export const REREAD_INTERVAL_MS = 4_000;

/**
 * 給查詢的 `refetchInterval` 用：最近一次讀取失敗時每隔 `REREAD_INTERVAL_MS` 再讀一次，讀到之後停止。
 * 櫃檯畫面常常整天開著沒人碰，網路恢復後要自己換回正常畫面。
 */
export function retryAfterFailure(query: { state: { status: string } }): number | false {
  return query.state.status === 'error' ? REREAD_INTERVAL_MS : false;
}
