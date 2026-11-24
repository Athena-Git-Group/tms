import type { ReactNode } from 'react';
import { ToastProvider } from './components/Toast';
import { useGuestLanguage } from './use-guest-language';

export const GUEST_OVERLAY_ID = 'guest-overlays';

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
