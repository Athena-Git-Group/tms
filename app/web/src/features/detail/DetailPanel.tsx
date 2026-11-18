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

/** 狀態標籤六種顏色的樣式，色碼照 `ui/ui-plan.md` 畫面 8 的表。 */
const TAG_STYLES: Record<StatusTagTone, string> = {
  yellow: 'bg-[#FFF1C2] text-[#6B4E00]',
  'brand-soft': 'bg-brand-container text-brand-on-container',
  gray: 'bg-[#E5E5EA] text-[#3C3C43]',
  brand: 'bg-brand text-white',
  green: 'bg-ok-bg text-ok-fg',
  red: 'bg-[#FFE4E1] text-[#B42318]',
};

/** 狀態訊息卡四種底色，照 `ui/ui-plan.md` 畫面 8。 */
const CARD_STYLES: Record<StatusCardTone, string> = {
  green: 'bg-ok-bg',
  'brand-soft': 'bg-brand-container',
  red: 'bg-[#FFE4E1]',
  white: 'bg-surface',
};

/** 狀態訊息卡圖示的顏色，跟著底色換，照雛形 `ui/desk.css` 的 `.info`。 */
const CARD_ICON_STYLES: Record<StatusCardTone, string> = {
  green: 'text-ok-fg',
  'brand-soft': 'text-brand',
  red: 'text-err-fg',
  white: 'text-text-4',
};

/** 歷程色點五種顏色，色碼照雛形 `ui/desk.js` 的歷程。 */
const DOT_STYLES: Record<TimelineDotTone, string> = {
  brand: 'bg-brand',
  gray: 'bg-[#8E8E93]',
  orange: 'bg-[#FF9500]',
  purple: 'bg-[#5E5CE6]',
  green: 'bg-[#34C759]',
};

/** 原文列認得的語言；其他語言只寫「原文：」。 */
const KNOWN_LANGUAGES = ['ja', 'en', 'ko', 'vi', 'zh'] as const;

/** 有翻譯內容時標題下的原文列：「{語言}原文：{住客打的字}」；沒有翻譯時沒有這一列。 */
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

/**
 * 單筆詳情面板：從畫面右側讓出 400px 顯示一張單。名稱是「{單號} 詳情」。
 *
 * 由上到下：關閉列與單號；白卡（房號與狀態標籤、標題、原文列、標題下那一行）；狀態訊息卡；
 * 打掃單已接與進行中時的「清潔 SOP」；「內容」六列；歷程；底部是派單區或「單據管理」動作列（見 `DetailFooter`）。
 * 回覆全文、住客原文是別人打的字，介面換語言時照原文顯示。
 */
export function DetailPanel({ taskId }: { taskId: string }) {
  const { t } = useTranslation();
  const closeDetail = useDeskPanelsStore((state) => state.closeDetail);
  const now = useCorrectedNow();
  const offsetMinutes = useServerClockStore((state) => state.serverUtcOffsetMinutes);
  const { data: task } = useQuery({ queryKey: ['desk-task', taskId], queryFn: () => readDeskTask(taskId) });
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

/** 底部動作列的按鈕：等寬並排、品牌色描邊，`solid` 實心、`danger` 紅色描邊，照雛形 `ui/desk.css` 的 `.action-bar`。 */
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

/**
 * 面板底部固定的動作列。
 * 待派單的單是「派單」區三格：「派給房務」「派給工務」直接派單，走和待派單頁同一支派單流程（浮條、復原、撞單都一樣），
 * 「當場回答…」打開當場回答面板。派出之後面板留著，看板一換成待認領這一區就收起來，不必等詳情重讀，免得被按第二次。
 * 其他狀態依登入者的角色畫「單據管理」動作列（`managementActions`），這一版點了沒有任何反應，也不送任何請求。
 */
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
    // 看板上有這張單就拿看板那一份去派，派單流程是照看板的樣子先畫、失敗時還原的。
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

/**
 * 看板資料每讀到一次新的，就把開著的這張單的詳情也重讀一次。
 * 同仁在手機上認領、完成之後，看板一更新，面板上的狀態標籤與狀態訊息卡就跟著換，不必重新整理網頁；
 * 讀的時候舊內容留著，不閃骨架。看板什麼時候會更新，由看板自己的讀取決定。
 */
function useRereadWithBoard(taskId: string) {
  const queryClient = useQueryClient();
  useEffect(
    () =>
      queryClient.getQueryCache().subscribe((event) => {
        if (
          event.type === 'updated' &&
          event.action.type === 'success' &&
          event.query.queryKey[0] === deskBoardQuery.queryKey[0]
        ) {
          void queryClient.invalidateQueries({ queryKey: ['desk-task', taskId] });
        }
      }),
    [queryClient, taskId],
  );
}

/**
 * 白卡：房號與狀態標籤一列，下面是標題、原文列（有翻譯內容才有）、灰字「{住客} · {類型} · {N} 分鐘前建立」。
 * 空房的單沒有住客那一段。詳情面板上不顯示住客人數，也不顯示急件。
 */
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

/**
 * 歷程：由舊到新，每筆左邊一個色點與往下的連接線，右邊一句話，下面灰字「{時間} · {操作者}」。
 * 規格還沒定句子的事件不畫，所以畫出來的筆數可能比後端回的少。
 */
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

/**
 * 狀態訊息卡：圓形資訊圖示、標題、說明。文字與底色由 `statusCard` 算，沒有訊息卡的狀況（待派單）不畫。
 */
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
