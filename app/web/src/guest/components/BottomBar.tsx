import type { ReactNode } from 'react';

/** 黏在畫面底部的操作列：半透明模糊底、一列滿寬按鈕，按鈕下可以有一行說明。 */
export function BottomBar({ children, note }: { children: ReactNode; note?: string }) {
  return (
    <div className="sticky bottom-0 z-20 mr-[calc(-1*max(16px,env(safe-area-inset-right)))] ml-[calc(-1*max(16px,env(safe-area-inset-left)))] flex flex-col gap-2 bg-guest-bar-bg pt-3 pr-(--guest-gutter-right) pb-[calc(12px+env(safe-area-inset-bottom))] pl-(--guest-gutter-left) shadow-[0_-.5px_0_var(--color-guest-separator)] backdrop-blur-[20px] backdrop-saturate-[1.8]">
      <div className="flex gap-3 [&>*]:min-w-0 [&>*]:flex-1">{children}</div>
      {note ? <p className="m-0 text-center text-guest-footnote text-guest-text-secondary">{note}</p> : null}
    </div>
  );
}
