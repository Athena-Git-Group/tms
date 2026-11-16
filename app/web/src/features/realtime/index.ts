/**
 * 即時更新的落點：以 `fetch` 讀員工事件串流（不用瀏覽器內建的 `EventSource`，它帶不了登入憑證的標頭）、
 * 收到訊號後重抓資料、連線異常時改成定時重抓，以及連線異常提醒列。
 * 串流的網址與憑證標頭用 `api/client.ts` 的 `backendUrl('/staff/stream')` 與 `authHeaders()`。
 * 這個資料夾目前還沒有內容。
 */
export {};
