import { Navigate, useParams } from 'react-router';
import { PageLoading } from '../components/PageLoading';
import { TitleBar } from '../components/TitleBar';
import { listableInfoPages, type ListedInfoPage } from '../lib/info-pages';
import { guestPath } from '../paths';
import { useGuestHome } from '../queries';
import { useReadySession } from '../session';

export function GuidePage() {
  const session = useReadySession();
  const home = useGuestHome(session.qrToken);
  const key = useParams().key ?? '';

  if (!home.data) return <PageLoading />;
  const page = listableInfoPages(home.data.infoPages).find((each) => each.key === key);
  if (!page) return <Navigate to={guestPath(session.qrToken)} replace />;
  return <InfoPageContent page={page} />;
}

function InfoPageContent({ page }: { page: ListedInfoPage }) {
  return (
    <>
      <TitleBar title={page.title} />
      <main className="flex min-h-[calc(100dvh-var(--spacing-guest-title-bar))] flex-col gap-4 pt-2 pb-[calc(48px+env(safe-area-inset-bottom))]">
        <p className="m-0 text-guest-body whitespace-pre-wrap [overflow-wrap:anywhere]">{page.body ?? ''}</p>
      </main>
    </>
  );
}
