import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { stageOf, type StageTone } from '../lib/stage';

const TONE: Record<StageTone, string> = {
  neutral: 'bg-guest-status-neutral-container text-guest-status-on-neutral-container',
  accepted: 'bg-guest-brand-container text-guest-brand-on-container-strong',
  doing: 'bg-guest-brand-primary text-guest-brand-on-primary',
  done: 'bg-guest-status-success-container text-guest-status-on-success-container',
};

export function StatusTag({ task }: { task: Pick<GuestTask, 'state'> }) {
  const { t } = useTranslation();
  const stage = stageOf(task);
  return (
    <span
      className={`inline-flex min-h-6 shrink-0 items-center gap-1 rounded-guest-pill px-2.5 py-0.5 text-guest-footnote font-semibold tracking-[-.08px] whitespace-nowrap ${TONE[stage.tone]}`}
    >
      {t(stage.labelKey)}
    </span>
  );
}
