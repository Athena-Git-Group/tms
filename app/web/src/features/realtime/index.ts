/**
 * 即時更新的落點：以 `fetch` 讀員工事件串流（不用瀏覽器內建的 `EventSource`，它帶不了登入憑證的標頭）、
 * 收到訊號後重抓資料，並判斷即時訊號可不可信（不可信時外框顯示提醒列、改成定時重抓）。
 *
 * 分層：
 * - `live-connection.ts` 是連線的狀態機：由連線事件與訊號算出連線走到哪一步、可不可信、重連等多久，全是純函式。
 * - `stream-client.ts` 只管連線、解析與計時器，把發生的事餵進狀態機；不認識 React Query 與看板。
 * - `refetch-on-signal.ts` 決定收到哪個訊號重抓什麼；不碰 `fetch` 與文字解析。
 * `use-live-updates.ts` 只把它們接起來，跟著登入憑證開關連線。
 */
export { boardPollMs, showsConnectionAlert, type LiveState } from './live-connection';
export { useLiveUpdates } from './use-live-updates';
