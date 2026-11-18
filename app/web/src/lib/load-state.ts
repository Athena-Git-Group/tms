export type LoadState = 'loading' | 'failed' | 'ready';

/**
 * 判斷畫面該顯示載入中、失敗或資料。重讀時查詢會暫時清掉 `isError`，
 * 所以另外用 `errorUpdatedAt` 判斷最近是否失敗過，沒失敗過時它是 0。
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
