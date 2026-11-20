/** 換 session 或第一次讀資料時整頁只有一個置中的轉圈，沒有字。 */
export function PageLoading() {
  return (
    <div role="status" aria-busy="true" className="flex min-h-dvh items-center justify-center text-guest-brand-primary">
      <span className="size-6 animate-spin rounded-full border-2 border-current border-r-transparent [animation-duration:.8s]" />
    </div>
  );
}
