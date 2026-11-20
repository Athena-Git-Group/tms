import type { ReactNode } from 'react';
import { Icon } from './Icon';
import type { IconName } from './icon-paths';

const ICON_TONE = {
  caution: 'text-guest-icon-caution',
  success: 'text-guest-icon-success',
  neutral: 'text-guest-text-secondary',
} as const;

/** 整頁置中的提示：無法使用頁、重試狀態與送出成功頁共用。 */
export function CenteredNotice({
  icon,
  tone,
  title,
  body,
  children,
}: {
  icon: IconName;
  tone: keyof typeof ICON_TONE;
  title: string;
  body?: string;
  children?: ReactNode;
}) {
  return (
    <main className="flex min-h-dvh flex-col justify-center pt-[calc(16px+env(safe-area-inset-top))] pb-[calc(48px+env(safe-area-inset-bottom))]">
      <div className="flex flex-col items-center gap-2 px-4 pt-12 pb-8 text-center">
        <Icon name={icon} className={`mb-2 text-[56px] ${ICON_TONE[tone]}`} />
        <h1 className="m-0 text-guest-center-title font-bold tracking-[.36px] text-balance">{title}</h1>
        {body ? <p className="m-0 max-w-[30em] text-guest-body text-guest-text-secondary">{body}</p> : null}
        {children ? <div className="mt-6 flex w-full flex-col gap-2">{children}</div> : null}
      </div>
    </main>
  );
}
