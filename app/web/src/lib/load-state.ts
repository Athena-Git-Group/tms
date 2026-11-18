export type LoadState = 'loading' | 'failed' | 'ready';

/**
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
