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

export function TriageSubtitle() {
  const { t } = useTranslation();
  const { data } = useQuery(deskBoardQuery);
  const now = useCorrectedNow();
  const rows = data ? triageTasks(data.tasks) : undefined;
  return <div className="min-h-[17px] truncate text-[13px] text-text-2">{triageSubtitleText(rows, now, t)}</div>;
}

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
