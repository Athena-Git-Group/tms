/**
 * 即時更新的落點：以 `fetch` 讀員工事件串流（不用瀏覽器內建的 `EventSource`，它帶不了登入憑證的標頭）、
 * 依訊號帶來的卡片逐張更新看板，必要時整份重讀，並判斷即時訊號可不可信（不可信時外框顯示提醒列、改成定時重抓）。
 *
 * 分層：
 * - `live-connection.ts` 是連線的狀態機：由連線事件與訊號算出連線走到哪一步、可不可信、重連等多久，全是純函式。
 * - `stream-client.ts` 只管連線、解析與計時器，把發生的事餵進狀態機；不認識 React Query 與看板。
 * - `stream-task.ts` 把 `task` 訊號的內容讀成有型別的卡片，之後的程式不再檢查後端送來的格式。
 * - 卡片換不換、整份重讀怎麼和讀取期間收到的卡片合併、換日等多久，是 `lib/board-sync.ts` 的純函式；
 *   每份快取記著收過的卡片、讀回來時合併，在 `api/desk.ts`。
 * - `refetch-on-signal.ts` 決定整份重讀什麼時候真的送出，保證同時最多一次在讀。
 * - `follow-board.ts` 決定每種訊號做什麼，並排換日的重讀。
 * `use-live-updates.ts` 只把它們接起來，跟著登入憑證開關連線。
 */
export { boardPollMs, showsConnectionAlert, type LiveState } from './live-connection';
export { useLiveUpdates } from './use-live-updates';
