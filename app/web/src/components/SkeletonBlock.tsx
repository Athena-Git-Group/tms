/** 載入中的灰色骨架塊，閃動照雛形；骨架沒有文字，不會被讀成空欄或 0。尺寸與圓角由呼叫端用 `className` 給。 */
export function SkeletonBlock({ className }: { className: string }) {
  return <span aria-hidden="true" className={`block animate-shimmer bg-line-soft ${className}`} />;
}
