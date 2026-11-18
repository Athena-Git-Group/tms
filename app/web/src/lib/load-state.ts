/** 一份要從後端讀的資料的三種情形：第一次還在讀、第一次就讀不到、已經讀到。 */
export type LoadState = 'loading' | 'failed' | 'ready';

/**
 * 由查詢結果判斷畫面該畫哪一種。
 * 已經讀到過資料之後重讀失敗，畫面留著舊資料，不換成讀取失敗。
 * 還沒讀到過資料、但失敗過一次之後，按「重試」或每 5 秒再試的那段時間仍算讀取失敗，不閃回骨架：
 * 重讀時查詢會暫時清掉失敗狀態，所以另外看「最近一次失敗的時間」（沒失敗過是 0）。
 */
export function loadStateOf({
  data,
  isError,
  errorUpdatedAt,
}: {
  data: unknown;
  isError: boolean;
  errorUpdatedAt: number;
}): LoadState {
  if (data) {
    return 'ready';
  }
  return isError || errorUpdatedAt > 0 ? 'failed' : 'loading';
}
