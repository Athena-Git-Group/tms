import { useTranslation } from 'react-i18next';
import type { DeskTask } from '../../api/types';
import type { AssignDept } from '../../lib/dispatch';
import { cardTitle } from '../../lib/card-text';
import { originalLineText, sourceText, waitedText } from '../../lib/triage-text';
import { waitTone, waitingMinutes, type WaitPillTone } from '../../lib/wait-pill';

const WAIT_TEXT_COLORS: Record<WaitPillTone, string> = {
  gray: 'text-text-2',
  orange: 'text-amber',
  red: 'text-err-fg',
  green: 'text-text-2',
};

const ACTION_BUTTON =
  'min-h-12 rounded-full border-[1.5px] border-solid border-brand bg-transparent px-[18px] text-base font-bold whitespace-nowrap text-brand';

interface TriageRowProps {
  task: DeskTask;
  now: Date | undefined;
  onOpenDetail: (taskId: string) => void;
  onAssign: (task: DeskTask, dept: AssignDept) => void;
  onAnswer: (taskId: string) => void;
}

export function TriageRow({ task, now, onOpenDetail, onAssign, onAnswer }: TriageRowProps) {
  const { t } = useTranslation();
  const minutes = now ? (waitingMinutes(task, now) ?? 0) : 0;
  const originalLine = originalLineText(task, t);
  return (
    <div
      onClick={() => onOpenDetail(task.id)}
      className="flex shrink-0 cursor-pointer flex-wrap items-start gap-3.5 rounded-tile bg-surface p-4"
    >
      <button
        type="button"
        aria-label={t('triage.opener', { roomNo: task.roomNo })}
        onClick={(event) => {
          event.stopPropagation();
          onOpenDetail(task.id);
        }}
        className="flex size-16 shrink-0 flex-col items-center justify-center rounded-2xl border-none bg-yellow-bg p-0 text-yellow-fg"
      >
        <b className="text-[21px] leading-[1.1] font-extrabold">{task.roomNo}</b>
        <span className="text-[11px] font-semibold">{t('board.lanes.floorName', { floor: task.floor })}</span>
      </button>
      <div className="flex min-w-0 flex-[1_1_300px] flex-col gap-1.5">
        <span className="text-[17px] leading-[1.4] font-semibold [overflow-wrap:anywhere]">{cardTitle(task, t)}</span>
        {originalLine && (
          <span className="text-[13px] leading-[1.4] text-text-2 [overflow-wrap:anywhere]">{originalLine}</span>
        )}
        <span className="flex flex-wrap items-center gap-1.5 text-[13px] text-text-2">
          <span>{task.guestName || t('triage.vacant')}</span>
          {task.vip && (
            <span className="rounded-md bg-warm-bg px-1.5 py-px text-[11px] font-extrabold text-warm-fg">{t('board.card.vip')}</span>
          )}
          <span>{sourceText(task, t)}</span>
          <span className={`font-bold ${WAIT_TEXT_COLORS[waitTone(minutes)]}`}>{waitedText(minutes, t)}</span>
        </span>
      </div>
      <div
        role="group"
        aria-label={t('triage.actions', { roomNo: task.roomNo })}
        onClick={(event) => event.stopPropagation()}
        className="flex flex-wrap gap-2 self-center"
      >
        <button type="button" className={ACTION_BUTTON} onClick={() => onAssign(task, 'housekeeping')}>
          {t('triage.assign.housekeeping')}
        </button>
        <button type="button" className={ACTION_BUTTON} onClick={() => onAssign(task, 'engineering')}>
          {t('triage.assign.engineering')}
        </button>
        <button type="button" className={ACTION_BUTTON} onClick={() => onAnswer(task.id)}>
          {t('triage.answer')}
        </button>
      </div>
    </div>
  );
}
