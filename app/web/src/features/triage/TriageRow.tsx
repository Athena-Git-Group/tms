import { useTranslation } from 'react-i18next';
import type { DeskTask } from '../../api/types';
import type { AssignDept } from '../../lib/dispatch';
import { cardTitle } from '../../lib/card-text';
import { originalLineText, sourceText, waitedText } from '../../lib/triage-text';
import { waitTone, waitingMinutes, type WaitPillTone } from '../../lib/wait-pill';

/** 「已等 N 分」的字色：灰色照一般文字，10 分起橘、20 分起紅，和看板膠囊同一組分級。 */
const WAIT_TEXT_COLORS: Record<WaitPillTone, string> = {
  gray: 'text-text-2',
  orange: 'text-amber',
  red: 'text-err-fg',
  green: 'text-text-2',
};

/** 三顆動作同一個樣子：品牌色外框、透明底，沒有哪一顆特別突出，系統不替櫃檯選部門。 */
const ACTION_BUTTON =
  'min-h-12 rounded-full border-[1.5px] border-solid border-brand bg-transparent px-[18px] text-base font-bold whitespace-nowrap text-brand';

interface TriageRowProps {
  task: DeskTask;
  /** 校正後的現在；還沒有時已等算 0 分。 */
  now: Date | undefined;
  /** 房號方塊與這一列動作以外的地方被點時，開這張單的詳情。 */
  onOpenDetail: (taskId: string) => void;
  /** 「派給房務」「派給工務」被點時，直接把這張單派給那個部門，不另外確認。 */
  onAssign: (task: DeskTask, dept: AssignDept) => void;
  /** 「當場回答…」被點時，開這張單的當場回答面板。 */
  onAnswer: (taskId: string) => void;
}

/**
 * 待派單的一列：左邊房號方塊（點了開詳情）、中間住客原話與住客資訊、右邊三顆動作。
 * 點這一列動作以外的地方也是開詳情。
 */
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
