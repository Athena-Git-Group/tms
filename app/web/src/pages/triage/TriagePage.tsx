import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { deskBoardQuery } from '../../api/desk';
import type { DeskMyToday } from '../../api/types';
import { CenteredNotice } from '../../components/CenteredNotice';
import { LoadFailed } from '../../components/LoadFailed';
import { SkeletonBlock } from '../../components/SkeletonBlock';
import { useAssignTask } from '../../features/dispatch';
import { TriageRow } from '../../features/triage';
import { loadStateOf } from '../../lib/load-state';
import { poolCount, triageTasks } from '../../lib/triage-list';
import { deptLoadText, triageEmptyText, triageSubtitleText } from '../../lib/triage-text';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import { useCorrectedNow } from '../../stores/server-clock';

/**
 * 待派單頁的頁副標，放在工具列的頁標題下面：有單寫「{N} 張等你派單 · 最久已等 {M} 分」，清單空了寫「目前沒有要派的單」。
 * 讀的是外框那一份看板資料；還沒讀到時不寫字，不先說沒有單。
 */
export function TriageSubtitle() {
  const { t } = useTranslation();
  const { data } = useQuery(deskBoardQuery);
  const now = useCorrectedNow();
  const rows = data ? triageTasks(data.tasks) : undefined;
  return <div className="min-h-[17px] truncate text-[13px] text-text-2">{triageSubtitleText(rows, now, t)}</div>;
}

/**
 * 待派單頁（路徑 `/triage`），畫在外框的內容區：頁首、需求清單與頁尾說明。
 * 清單就是看板資料裡的待派單，和總覽看板讀同一份快取，不另外向後端讀。
 * 系統不建議部門，每一列只有「派給房務」「派給工務」「當場回答…」三顆動作，由櫃檯自己判斷。
 * 清單空了只畫空狀態；第一次讀取畫骨架、讀不到畫讀取失敗，都不會顯示成沒有單。
 */
export function TriagePage() {
  const { t } = useTranslation();
  const { data, isError, errorUpdatedAt, refetch } = useQuery(deskBoardQuery);
  const now = useCorrectedNow();
  const openDetail = useDeskPanelsStore((state) => state.openDetail);
  const openAnswer = useDeskPanelsStore((state) => state.openAnswer);
  const assignTask = useAssignTask();
  const load = loadStateOf({ data, isError, errorUpdatedAt });

  if (load === 'failed') {
    return (
      <section data-page="triage" className="flex min-h-0 flex-auto flex-col px-4 pb-6">
        <LoadFailed onRetry={() => void refetch()} />
      </section>
    );
  }
  if (load === 'loading' || !data) {
    return (
      <section data-page="triage" aria-busy="true" className="flex min-h-0 flex-auto flex-col gap-2.5 px-4 pb-6">
        <SkeletonBlock className="h-24 rounded-tile" />
        <SkeletonBlock className="h-24 rounded-tile" />
        <SkeletonBlock className="h-24 rounded-tile" />
      </section>
    );
  }

  const rows = triageTasks(data.tasks);
  if (rows.length === 0) {
    return (
      <section data-page="triage" className="flex min-h-0 flex-auto flex-col px-4 pb-6">
        <TriageEmpty myToday={data.myToday} />
      </section>
    );
  }
  return (
    <section data-page="triage" className="flex min-h-0 flex-auto flex-col gap-2.5 overflow-y-auto px-4 pb-6">
      <div className="flex shrink-0 flex-wrap items-center gap-2 px-1">
        <span className="rounded-full bg-surface px-2.5 py-1 text-[13px] font-semibold whitespace-nowrap text-brand-on-container">
          {deptLoadText('housekeeping', poolCount(data.tasks, 'housekeeping'), t)}
        </span>
        <span className="rounded-full bg-surface px-2.5 py-1 text-[13px] font-semibold whitespace-nowrap text-brand-on-container">
          {deptLoadText('engineering', poolCount(data.tasks, 'engineering'), t)}
        </span>
        <span className="grow" />
        <span className="text-xs text-text-3">{t('triage.help')}</span>
      </div>
      {rows.map((task) => (
        <TriageRow
          key={task.id}
          task={task}
          now={now}
          onOpenDetail={openDetail}
          onAssign={(target, dept) => void assignTask(target, dept, 'button')}
          onAnswer={openAnswer}
        />
      ))}
      <span className="shrink-0 px-1 pt-1 text-[13px] leading-normal text-text-2">{t('triage.footer')}</span>
    </section>
  );
}

/**
 * 待派單清單空了的時候畫在清單位置的說明，這時沒有頁首與頁尾。
 * 今天派過單或當場回答過是綠色勾的「都派完了」，兩個都沒有是灰色收件匣的「目前沒有要派的單」。
 * 派單或復原後看板會重讀，張數跟著後端更新。
 */
function TriageEmpty({ myToday }: { myToday: DeskMyToday }) {
  const { t } = useTranslation();
  const { done, title, hint } = triageEmptyText(myToday, t);
  return (
    <CenteredNotice
      icon={
        done ? (
          <>
            <circle cx="12" cy="12" r="9" />
            <path d="M8 12.5l3 3 5-6" />
          </>
        ) : (
          <>
            <path d="M3 13l3-7h12l3 7v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <path d="M3 13h5a4 4 0 0 0 8 0h5" />
          </>
        )
      }
      iconClassName={done ? 'text-[#34C759]' : undefined}
      title={title}
      hint={hint}
    />
  );
}
