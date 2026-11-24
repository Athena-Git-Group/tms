import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { Icon } from '../components/Icon';
import { PageLoading } from '../components/PageLoading';
import { RequestRow } from '../components/RequestRow';
import { TitleBar } from '../components/TitleBar';
import { requestLink } from '../lib/back-target';
import { splitSections, type StageSection } from '../lib/stage';
import { useGuestRequests } from '../queries';
import { useReadySession } from '../session';

export function RequestsPage() {
  const { t } = useTranslation();
  const session = useReadySession();
  const requests = useGuestRequests(session.qrToken);

  if (!requests.data) return <PageLoading />;
  const sections = splitSections(requests.data);
  return (
    <>
      <TitleBar title={t('guest.requests.title')} />
      <main className="flex min-h-[calc(100dvh-var(--spacing-guest-title-bar))] flex-col gap-5 pt-2 pb-[calc(48px+env(safe-area-inset-bottom))]">
        {sections.length === 0 ? <EmptyRequests /> : null}
        {sections.map(({ section, tasks }) => (
          <Section key={section} title={t(SECTION_TITLE[section])} tasks={tasks} qrToken={session.qrToken} />
        ))}
        <p className="m-0 px-4 text-guest-footnote tracking-[-.08px] text-guest-text-secondary">{t('guest.requests.note')}</p>
      </main>
    </>
  );
}

const SECTION_TITLE = {
  active: 'guest.requests.active',
  ended: 'guest.requests.ended',
} as const satisfies Record<StageSection, string>;

function EmptyRequests() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-2 px-4 pt-12 pb-8 text-center">
      <Icon name="list_bullet" className="mb-2 text-[56px] text-guest-text-secondary" />
      <h2 className="m-0 text-guest-center-title font-bold tracking-[.36px] text-balance">{t('guest.requests.emptyTitle')}</h2>
      <p className="m-0 max-w-[30em] text-guest-body text-guest-text-secondary">{t('guest.requests.emptyBody')}</p>
    </div>
  );
}

function Section({ title, tasks, qrToken }: { title: string; tasks: GuestTask[]; qrToken: string }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="m-0 text-guest-section-title font-bold tracking-[.38px]">{title}</h2>
      <ul className="m-0 flex list-none flex-col overflow-hidden rounded-guest-lg bg-guest-surface p-0 shadow-guest-card">
        {tasks.map((task) => (
          <RequestRow key={task.id} task={task} link={requestLink(qrToken, task.id, 'list')} />
        ))}
      </ul>
    </section>
  );
}
