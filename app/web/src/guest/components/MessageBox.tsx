import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { messageBox, type MessageKind } from '../lib/stage';
import { Icon } from './Icon';
import type { IconName } from './icon-paths';

const LOOK: Record<MessageKind, { box: string; icon: IconName; iconColor: string }> = {
  triage: {
    box: 'bg-guest-brand-container text-guest-brand-on-container-strong',
    icon: 'info_circle_fill',
    iconColor: 'text-guest-brand-primary',
  },
  answer: {
    box: 'bg-guest-brand-container text-guest-brand-on-container-strong',
    icon: 'text_bubble',
    iconColor: 'text-guest-brand-primary',
  },
  completed: {
    box: 'bg-guest-status-success-container text-guest-status-on-success-container',
    icon: 'checkmark_circle_fill',
    iconColor: 'text-guest-icon-success',
  },
  cancelled: {
    box: 'bg-guest-status-neutral-container text-guest-status-on-neutral-container',
    icon: 'info_circle_fill',
    iconColor: 'text-guest-status-on-neutral-container',
  },
};

/** 進度頁第一張卡下方的狀態訊息框：第一行標題、第二行內文，色調與圖示由種類決定。 */
export function MessageBox({ task }: { task: GuestTask }) {
  const { t } = useTranslation();
  const view = messageBox(task);
  if (!view) return null;
  const look = LOOK[view.kind];
  return (
    <div role="note" className={`grid grid-cols-[26px_minmax(0,1fr)] items-start gap-x-3 rounded-guest-card px-3.5 py-3 ${look.box}`}>
      <Icon name={look.icon} className={`mt-px text-[26px] ${look.iconColor}`} />
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="text-[17px] leading-6 font-semibold tracking-[-.43px]">{t(view.titleKey)}</span>
        <span className="text-guest-callout tracking-[-.23px] whitespace-pre-line [overflow-wrap:anywhere]">
          {'text' in view.body ? view.body.text : t(view.body.key)}
        </span>
      </div>
    </div>
  );
}
