import { createContext, type ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { Icon } from './Icon';

export type ToastTone = 'ok' | 'warn';

interface ToastMessage {
  id: number;
  text: string;
  tone: ToastTone;
}

type ShowToast = (text: string, tone: ToastTone) => void;

const TOAST_MS = 2_600;

const ToastContext = createContext<ShowToast | null>(null);

/** 外框層級的提示浮層：同時只有一則，新的蓋掉舊的，2.6 秒後自己消失；換頁時照樣留著。 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const show = useCallback<ShowToast>((text, tone) => setToast((last) => ({ id: (last?.id ?? 0) + 1, text, tone })), []);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [toast]);

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && <ToastBar key={toast.id} toast={toast} />}
    </ToastContext.Provider>
  );
}

export function useToast(): ShowToast {
  const show = useContext(ToastContext);
  if (!show) throw new Error('useToast 要在住客外框裡用');
  return show;
}

/** 固定在畫面頂端、不接收點擊，蓋在標題列上時底下的返回鈕照樣按得到。 */
function ToastBar({ toast }: { toast: ToastMessage }) {
  return (
    <div
      role="status"
      className="pointer-events-none fixed top-[calc(8px+env(safe-area-inset-top))] right-(--guest-gutter-right) left-(--guest-gutter-left) z-50 mx-auto flex min-h-[52px] max-w-[calc(var(--container-guest-content)-2*var(--spacing-guest-gutter))] items-center gap-2.5 rounded-guest-card bg-guest-toast-bg px-4 py-2 text-guest-callout tracking-[-.23px] text-guest-on-toast shadow-guest-toast motion-safe:animate-guest-drop"
    >
      <Icon
        name={toast.tone === 'ok' ? 'checkmark_circle_fill' : 'exclamationmark_triangle_fill'}
        className={`text-[20px] ${toast.tone === 'ok' ? 'text-guest-icon-success' : 'text-guest-icon-caution'}`}
      />
      <span className="min-w-0 flex-auto">{toast.text}</span>
    </div>
  );
}
