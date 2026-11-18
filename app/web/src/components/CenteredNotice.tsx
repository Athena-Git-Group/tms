import type { ReactNode } from 'react';

/**
 * 內容區正中央的一塊說明：上面一個大圖示，下面一行粗體標題、一行說明，需要時再接一顆按鈕。
 * 讀取失敗與待派單頁的兩種空狀態都長這樣，只換圖示、顏色與字。
 * 標題不是頁標題，所以用粗體字而不是標題元素。
 */
export function CenteredNotice({
  icon,
  iconClassName,
  title,
  hint,
  children,
}: {
  /** 圖示的線條，畫在 24×24 的框裡。 */
  icon: ReactNode;
  /** 圖示的顏色，預設是淡灰。 */
  iconClassName?: string;
  title: string;
  hint: string;
  /** 說明下面的按鈕；沒有就不畫。 */
  children?: ReactNode;
}) {
  return (
    <div className="flex min-h-[280px] flex-auto flex-col items-center justify-center px-8 pt-6 pb-10 text-center">
      <svg
        viewBox="0 0 24 24"
        aria-hidden="true"
        className={`size-14 fill-none stroke-current stroke-[1.3] ${iconClassName ?? 'text-text-4'}`}
      >
        {icon}
      </svg>
      <b className="mt-3.5 text-[22px] leading-7">{title}</b>
      <span className="mt-1.5 text-[15px] leading-5 text-text-2">{hint}</span>
      {children}
    </div>
  );
}
