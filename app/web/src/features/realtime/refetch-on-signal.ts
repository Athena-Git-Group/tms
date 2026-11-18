import type { QueryClient } from '@tanstack/react-query';
import { deskBoardQuery } from '../../api/desk';

/**
 * 依訊號名稱決定要不要重抓看板。只認訊號名稱，不碰連線與文字解析（那是 `stream-client.ts` 的事）。
 *
 * - `task`：某張單變了，馬上重抓；正在讀的那一次可能是變化之前送出的，所以取消它重來。
 * - `resync`：連上或重新連上時後端提醒「連線以外的變化不會補送」，重抓一次。
 *   這時看板通常正在第一次讀或剛讀完，取消正在讀的那一次會讓第一次載入白白重來，所以讓它讀完。
 *   看板正處於讀取失敗時不重抓，讓讀取失敗畫面照外框的規則每 5 秒再試，使用者看到的再試間隔才一致。
 * - `recovered`：後端離開降級，降級期間的訊號可能漏了，和 `resync` 一樣重抓一次。
 * - 其他（`init`、`heartbeat`、`degraded`）：不重抓；連線異常的提醒與定時重抓見 `live-connection.ts` 與外框。
 *
 * 開著的詳情面板不在這裡另外重抓：它在看板每次讀成功後自己重讀（見 `DetailPanel` 的 `useRereadWithBoard`）。
 */
export function refetchForSignal(signal: string, queryClient: QueryClient): void {
  if (signal === 'task') {
    void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
    return;
  }
  if ((signal === 'resync' || signal === 'recovered') &&queryClient.getQueryState(deskBoardQuery.queryKey)?.status === 'success') {
    void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey }, { cancelRefetch: false });
  }
}
