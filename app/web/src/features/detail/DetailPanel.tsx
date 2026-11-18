import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { TFunction } from 'i18next';
import { useEffect, useId } from 'react';
import { useTranslation } from 'react-i18next';
import { deskBoardQuery, readDeskTask } from '../../api/desk';
import type { DeskTaskDetail } from '../../api/types';
import { cardTitle } from '../../lib/card-text';
import { hasDispatchArea, managementActions, type ManagementAction } from '../../lib/detail-actions';
import { createdAgoText, detailContentRows } from '../../lib/detail-content';
import { displayString } from '../../lib/display-text';
import { statusCard, statusTag, type StatusCardTone, type StatusTagTone } from '../../lib/status-card';
import { timelineMeta, timelineSentence, type TimelineDotTone } from '../../lib/timeline-sentence';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import { useCorrectedNow, useServerClockStore } from '../../stores/server-clock';
import { useSessionStore } from '../../stores/session';
import { useAssignTask } from '../dispatch';

const TAG_STYLES: Record<StatusTagTone, string> = {
  yellow: 'bg-[#FFF1C2] text-[#6B4E00]',
  'brand-soft': 'bg-brand-container text-brand-on-container',
  gray: 'bg-[#E5E5EA] text-[#3C3C43]',
  brand: 'bg-brand text-white',
  green: 'bg-ok-bg text-ok-fg',
  red: 'bg-[#FFE4E1] text-[#B42318]',
};

const CARD_STYLES: Record<StatusCardTone, string> = {
  green: 'bg-ok-bg',
  'brand-soft': 'bg-brand-container',
  red: 'bg-[#FFE4E1]',
  white: 'bg-surface',
};

const CARD_ICON_STYLES: Record<StatusCardTone, string> = {
  green: 'text-ok-fg',
  'brand-soft': 'text-brand',
  red: 'text-err-fg',
  white: 'text-text-4',
};

const DOT_STYLES: Record<TimelineDotTone, string> = {
  brand: 'bg-brand',
  gray: 'bg-[#8E8E93]',
  orange: 'bg-[#FF9500]',
  purple: 'bg-[#5E5CE6]',
  green: 'bg-[#34C759]',
};

const KNOWN_LANGUAGES = ['ja', 'en', 'ko', 'vi', 'zh'] as const;

function originalLine(task: DeskTaskDetail, t: TFunction): string | null {
  const guestText = task.guestText;
  if (!task.guestTyped || !guestText?.translated) {
    return null;
  }
  const base = guestText.lang?.split('-')[0];
  const lang = KNOWN_LANGUAGES.find((known) => known === base);
  return lang
    ? t('detail.original', { lang: t(`detail.languages.${lang}`), text: guestText.original })
    : t('detail.originalPlain', { text: guestText.original });
}

export function DetailPanel({ taskId }: { taskId: string }) {
  const { t } = useTranslation();
  const closeDetail = useDeskPanelsStore((state) => state.closeDetail);
  const now = useCorrectedNow();
  const offsetMinutes = useServerClockStore((state) => state.serverUtcOffsetMinutes);
  const { data: task } = useQuery({ queryKey: ['desk-task', taskId], queryFn: ({ signal }) => readDeskTask(taskId, signal) });
  useRereadWithBoard(taskId);
  const contentId = useId();
  const timelineId = useId();

  return (
    <aside
      aria-label={t('detail.label', { taskNo: task?.displayNo ?? '' })}
      aria-busy={!task}
      className="flex w-detail-panel shrink-0 flex-col border-l-[0.5px] border-line bg-bg"
    >
      <div className="grid min-h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center px-2 pt-1.5">
        <span>
          <button
            type="button"
            onClick={closeDetail}
            className="inline-flex min-h-touch items-center border-none bg-transparent px-2 text-[17px] text-brand"
          >
            {t('detail.close')}
          </button>
        </span>
        <span className="text-[17px] font-semibold whitespace-nowrap">{task?.displayNo}</span>
        <span />
      </div>
      {task && (
        <>
          <div className="flex min-h-0 flex-auto flex-col gap-3 overflow-y-auto px-4 pb-6 *:shrink-0">
            <DetailHeader task={task} now={now} />
            <StatusCardBlock task={task} now={now} offsetMinutes={offsetMinutes} />
            {task.type === 'cleaning' && (task.state === 'accepted' || task.state === 'doing') && task.sop && (
              <section aria-label={t('detail.sop')} className="flex flex-col gap-2 rounded-2xl bg-surface px-4 py-3.5">
                <div className="flex justify-between text-[15px] font-semibold">
                  <span>{t('detail.sop')}</span>
                  <span className="font-normal text-text-2">
                    {t('detail.sopProgress', { done: task.sop.done, total: task.sop.total })}
                  </span>
                </div>
                <div
                  role="progressbar"
                  aria-label={t('detail.sop')}
                  aria-valuemin={0}
                  aria-valuemax={task.sop.total}
                  aria-valuenow={task.sop.done}
                  className="h-2 overflow-hidden rounded-full bg-line"
                >
                  <div
                    className="h-full rounded-full bg-brand"
                    style={{ width: `${task.sop.total > 0 ? Math.round((task.sop.done / task.sop.total) * 100) : 0}%` }}
                  />
                </div>
              </section>
            )}
            <section aria-labelledby={contentId} className="flex flex-col gap-1.5">
              <h3 id={contentId} className="m-0 px-4 text-[13px] font-normal text-text-2">
                {t('detail.content.title')}
              </h3>
              <dl className="m-0 overflow-hidden rounded-card bg-surface">
                {detailContentRows(task, now, offsetMinutes, t).map(({ label, value }) => (
                  <div key={label} className="flex gap-3 px-4 py-[11px] text-[15px] [&+&]:border-t-[0.5px] [&+&]:border-line">
                    <dt className="max-w-[45%] flex-none text-text-2">{label}</dt>
                    <dd className="m-0 min-w-0 flex-auto text-right [overflow-wrap:anywhere]">{value}</dd>
                  </div>
                ))}
              </dl>
            </section>
            <section aria-labelledby={timelineId} className="flex flex-col gap-1.5">
              <h3 id={timelineId} className="m-0 px-4 text-[13px] font-normal text-text-2">
                {t('detail.timeline')}
              </h3>
              <Timeline task={task} now={now} offsetMinutes={offsetMinutes} />
            </section>
          </div>
          <DetailFooter task={task} />
        </>
      )}
    </aside>
  );
}

const BAR_BUTTON =
  'min-h-[54px] min-w-0 flex-[1_1_0] rounded-full border-[1.5px] border-solid px-2 text-[17px] font-semibold whitespace-nowrap';
const BAR_OUTLINE = `${BAR_BUTTON} border-brand bg-transparent text-brand`;
const BAR_SOLID = `${BAR_BUTTON} border-brand bg-brand text-white`;
const BAR_DANGER = `${BAR_BUTTON} border-danger bg-transparent text-danger`;

const BAR = 'flex shrink-0 gap-3 border-t-[0.5px] border-line bg-surface px-4 pt-3 pb-4';

const MANAGEMENT_STYLES: Record<ManagementAction, string> = {
  cancel: BAR_DANGER,
  defer: BAR_OUTLINE,
  assign: BAR_SOLID,
  reassign: BAR_SOLID,
};

function DetailFooter({ task }: { task: DeskTaskDetail }) {
  const { t } = useTranslation();
  const role = useSessionStore((state) => state.session?.role);
  const openAnswer = useDeskPanelsStore((state) => state.openAnswer);
  const assignTask = useAssignTask();
  const { data: card } = useQuery({
    ...deskBoardQuery,
    enabled: false,
    select: (board) => board.tasks.find((item) => item.id === task.id),
  });

  if (hasDispatchArea(task) && (!card || hasDispatchArea(card))) {
    const target = card ?? task;
    return (
      <div role="group" aria-label={t('detail.dispatchArea')} className={BAR}>
        <button type="button" className={BAR_OUTLINE} onClick={() => void assignTask(target, 'housekeeping', 'button')}>
          {t('triage.assign.housekeeping')}
        </button>
        <button type="button" className={BAR_OUTLINE} onClick={() => void assignTask(target, 'engineering', 'button')}>
          {t('triage.assign.engineering')}
        </button>
        <button type="button" className={BAR_SOLID} onClick={() => openAnswer(task.id)}>
          {t('detail.answer')}
        </button>
      </div>
    );
  }
  const actions = managementActions(role, task);
  if (actions.length === 0) return null;
  return (
    <div role="group" aria-label={t('detail.management.label')} className={BAR}>
      {actions.map((action) => (
        <button key={action} type="button" className={MANAGEMENT_STYLES[action]}>
          {t(`detail.management.${action}`)}
        </button>
      ))}
    </div>
  );
}

function useRereadWithBoard(taskId: string) {
  const queryClient = useQueryClient();
  useEffect(
    () =>
      queryClient.getQueryCache().subscribe((event) => {
        if (
          event.type === 'updated' &&
          event.action.type === 'success' &&
          !event.action.manual &&
          event.query.queryKey[0] === deskBoardQuery.queryKey[0]
        ) {
          void queryClient.invalidateQueries({ queryKey: ['desk-task', taskId] });
        }
      }),
    [queryClient, taskId],
  );
}

function DetailHeader({ task, now }: { task: DeskTaskDetail; now: Date | undefined }) {
  const { t } = useTranslation();
  const tag = statusTag(task);
  const original = originalLine(task, t);
  const created = t('detail.created', { ago: createdAgoText(task, now, t) });
  const type = t(`board.card.types.${task.type}`);
  const guestName = task.guest?.name;
  return (
    <div className="flex flex-col gap-2 rounded-tile bg-surface p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="m-0 text-[34px] leading-[1.1] font-extrabold tracking-[-0.5px]">{task.roomNo}</h2>
        {task.vip && (
          <span className="rounded-lg bg-warm-bg px-2 py-0.5 text-xs font-extrabold text-warm-fg">
            {t('board.card.vip')}
          </span>
        )}
        <span className="grow" />
        <span className={`rounded-full px-2.5 py-1 text-[13px] font-bold whitespace-nowrap ${TAG_STYLES[tag.tone]}`}>
          {displayString(tag.text, t)}
        </span>
      </div>
      <h3 className="m-0 text-lg leading-[1.4] font-semibold [overflow-wrap:anywhere]">{cardTitle(task, t)}</h3>
      {original && <span className="text-sm leading-[1.45] text-text-2 [overflow-wrap:anywhere]">{original}</span>}
      <span className="text-[13px] text-text-2">
        {guestName
          ? t('detail.subtitle', { guest: guestName, type, created })
          : t('detail.subtitleVacant', { type, created })}
      </span>
    </div>
  );
}

function Timeline({ task, now, offsetMinutes }: { task: DeskTaskDetail; now: Date | undefined; offsetMinutes: number }) {
  const { t } = useTranslation();
  const rows = task.timeline.flatMap((entry) => {
    const sentence = timelineSentence(entry, task);
    return sentence ? [{ entry, sentence }] : [];
  });
  return (
    <ol className="m-0 list-none rounded-card bg-surface px-4 py-3.5">
      {rows.map(({ entry, sentence }) => (
        <li key={entry.seq} className="group flex gap-3">
          <div aria-hidden="true" className="flex w-3.5 shrink-0 flex-col items-center">
            <i className={`mt-[5px] size-2.5 shrink-0 rounded-full ${DOT_STYLES[sentence.dot]}`} />
            <s className="w-0.5 flex-auto bg-[#E5E5EA] group-last:bg-transparent" />
          </div>
          <div className="min-w-0 flex-auto pb-3.5">
            <b className="block text-[15px] leading-[1.4] font-normal [overflow-wrap:anywhere]">
              {displayString(sentence.text, t)}
            </b>
            <span className="text-xs text-text-2">
              {displayString(timelineMeta(entry, sentence.actor, now, offsetMinutes), t)}
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}

function StatusCardBlock({
  task,
  now,
  offsetMinutes,
}: {
  task: DeskTaskDetail;
  now: Date | undefined;
  offsetMinutes: number;
}) {
  const { t } = useTranslation();
  const card = now ? statusCard(task, now, offsetMinutes) : null;
  if (!card) return null;
  const title = displayString(card.title, t);
  const description = card.description ? displayString(card.description, t) : null;
  return (
    <section aria-label={t('detail.statusCard')} className={`flex items-start gap-3 rounded-2xl px-4 py-3 ${CARD_STYLES[card.tone]}`}>
      <svg viewBox="0 0 24 24" aria-hidden="true" className={`size-6 shrink-0 fill-none stroke-current stroke-[1.8] ${CARD_ICON_STYLES[card.tone]}`}>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 11v5M12 8v.01" />
      </svg>
      <div className="min-w-0">
        <b className="block text-[15px] font-semibold">{title}</b>
        {description && (
          <span className="mt-0.5 block text-[13px] leading-[1.45] text-text-2 [overflow-wrap:anywhere]">
            {description}
          </span>
        )}
      </div>
    </section>
  );
}
