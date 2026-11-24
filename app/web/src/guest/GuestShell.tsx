import type { ReactNode } from 'react';
import { ToastProvider } from './components/Toast';
import { useGuestLanguage } from './use-guest-language';

export const GUEST_OVERLAY_ID = 'guest-overlays';

/**
 * 住客的外框：手機直向為基準、單欄、內容最寬 480 置中、整頁上下捲。高度用 `dvh`，手機瀏覽器的工具列收放時不會多出捲動；
 * 左右留安全區域。底部的安全區域由各頁自己留，頁底操作列才能把它包在自己的內距裡。固定淺色，表單元件與捲軸也跟著淺色。提示浮層與對話框畫進最後那一層。
 */
export function GuestShell({ children }: { children: ReactNode }) {
  useGuestLanguage();
  return (
    <div className="min-h-dvh bg-guest-bg font-guest text-guest-body tracking-[-.43px] text-guest-text [color-scheme:light] [-webkit-tap-highlight-color:transparent] [&_:is(button,a,input,[tabindex]):focus-visible]:outline-2 [&_:is(button,a,input,[tabindex]):focus-visible]:outline-offset-2 [&_:is(button,a,input,[tabindex]):focus-visible]:outline-guest-brand-primary">
      <ToastProvider>
        <div className="mx-auto w-full max-w-guest-content pr-(--guest-gutter-right) pl-(--guest-gutter-left)">
          {children}
        </div>
        <div id={GUEST_OVERLAY_ID} className="relative z-40" />
      </ToastProvider>
    </div>
  );
}
