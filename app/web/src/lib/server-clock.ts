/**
 * 用後端的時間校正「現在」：畫面上的「等 N 分」「做 N 分」都拿校正後的現在去算，
 * 櫃檯電腦的時鐘不準時才不會算錯分鐘數。
 *
 * 做法分兩步。每次後端回應帶回它的時間（看板與詳情回應的 `serverTime`）時，先算出後端和這台裝置差多少；
 * 之後要用「現在」時，再把這個差加回裝置的時間。差值存在哪裡、多久重算一次分鐘數，由用到的畫面決定。
 *
 * 這個檔目前只有函式的外形，還沒有實作，呼叫任何一個函式都會丟出 `not implemented`。
 */

/**
 * 後端時間減掉裝置時間的差，單位是毫秒；後端比較快時是正數。
 * `serverTime` 是後端回應裡的時刻字串，`deviceNow` 是收到回應當下這台裝置的時間。
 */
export function clockOffsetMs(_serverTime: string, _deviceNow: Date): number {
  throw new Error('not implemented: lib/server-clock.ts clockOffsetMs');
}

/** 校正後的現在：裝置的時間加上 `clockOffsetMs` 算出的差。 */
export function correctedNow(_offsetMs: number, _deviceNow: Date): Date {
  throw new Error('not implemented: lib/server-clock.ts correctedNow');
}
