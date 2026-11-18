/**
 * 用後端的時間校正「現在」：畫面上的「等 N 分」「做 N 分」都拿校正後的現在去算，
 * 櫃檯電腦的時鐘不準時才不會算錯分鐘數。
 *
 * 做法分兩步。每次後端回應帶回它的時間（看板與詳情回應的 `serverTime`）時，先算出後端和這台裝置差多少；
 * 之後要用「現在」時，再把這個差加回裝置的時間。差值存在哪裡、多久重算一次分鐘數，由用到的畫面決定。
 */

/**
 * 後端時間減掉裝置時間的差，單位是毫秒；後端比較快時是正數。
 * `serverTime` 是後端回應裡的時刻字串，`deviceNow` 是收到回應當下這台裝置的時間。
 */
export function clockOffsetMs(serverTime: string, deviceNow: Date): number {
  return Date.parse(serverTime) - deviceNow.getTime();
}

/**
 * 某個時刻在某個時區偏移（分鐘，`+08:00` 是 480）下是哪一天，寫成 `YYYY-MM-DD`。
 * 看板資料還沒讀到、沒有後端給的營業日時，頁副標用它先寫出今天的日期。
 */
export function dateAtUtcOffset(instant: Date, offsetMinutes: number): string {
  return new Date(instant.getTime() + offsetMinutes * 60_000).toISOString().slice(0, 10);
}

/** 校正後的現在：裝置的時間加上 `clockOffsetMs` 算出的差。 */
export function correctedNow(offsetMs: number, deviceNow: Date): Date {
  return new Date(deviceNow.getTime() + offsetMs);
}

/**
 * 後端時刻字串尾端的時區偏移，換成分鐘：`+08:00` 是 480、`-05:30` 是 -330、`Z` 是 0。
 * 飯店的時區就是後端伺服器的時區（使用者裁決），畫面上顯示「時:分」都照這個偏移，不照這台裝置的時區。
 * 字串尾端沒有可辨認的偏移時回 `null`，由呼叫的一方決定沿用哪個值。
 */
export function utcOffsetMinutes(serverTime: string): number | null {
  const match = /(?:(Z)|([+-])(\d{2}):?(\d{2}))$/i.exec(serverTime.trim());
  if (!match) {
    return null;
  }
  if (match[1]) {
    return 0;
  }
  const minutes = Number(match[3]) * 60 + Number(match[4]);
  return match[2] === '-' ? -minutes : minutes;
}
