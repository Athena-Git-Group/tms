import { useTranslation } from 'react-i18next';
import type { FormNotice } from '../lib/quota';
import { Icon } from './Icon';
import type { IconName } from './icon-paths';

const LOOK = {
  caution: {
    box: 'bg-guest-brand-accent-container text-guest-brand-on-accent-container',
    icon: 'exclamationmark_triangle_fill',
    iconColor: 'text-guest-icon-caution',
  },
  error: {
    box: 'bg-guest-status-error-container text-guest-status-on-error-container',
    icon: 'xmark_circle_fill',
    iconColor: 'text-guest-icon-error',
  },
} as const satisfies Record<FormNotice['tone'], { box: string; icon: IconName; iconColor: string }>;

export function InlineNotice({ notice }: { notice: FormNotice }) {
  const { t } = useTranslation();
  const look = LOOK[notice.tone];
  return (
    <div role="alert" className={`grid grid-cols-[26px_minmax(0,1fr)] items-start gap-x-3 rounded-guest-card px-3.5 py-3 ${look.box}`}>
      <Icon name={look.icon} className={`mt-px text-[26px] ${look.iconColor}`} />
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="text-[17px] leading-6 font-semibold tracking-[-.43px]">{t(notice.title.key, notice.title.params)}</span>
        <span className="text-guest-callout tracking-[-.23px] whitespace-pre-line [overflow-wrap:anywhere]">
          {t(notice.body.key, notice.body.params)}
        </span>
      </div>
    </div>
  );
}
