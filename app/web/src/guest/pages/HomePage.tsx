import { useTranslation } from 'react-i18next';
import { PageLoading } from '../components/PageLoading';
import { useGuestHome, useGuestRequests } from '../queries';
import { useReadySession } from '../session';
import { useDocumentTitle } from '../use-document-title';
import { ASK_ENTRY, ComingSoonEntry } from './home/ComingSoon';
import { EmergencyNote } from './home/EmergencyNote';
import { GuideList } from './home/GuideList';
import { Hello } from './home/Hello';
import { MyRequests } from './home/MyRequests';
import { HeroCards } from './home/HeroCards';
import { Services } from './home/Services';
import { TopBar } from './home/TopBar';

export function HomePage() {
  const { t } = useTranslation();
  const session = useReadySession();
  const home = useGuestHome(session.qrToken);
  const requests = useGuestRequests(session.qrToken);
  useDocumentTitle(t('guest.roomName', { roomNo: session.roomNo }));

  if (!home.data || !requests.data) return <PageLoading />;

  return (
    <main className="flex min-h-dvh flex-col gap-[18px] pt-[calc(14px+env(safe-area-inset-top))] pb-[calc(32px+env(safe-area-inset-bottom))]">
      <TopBar roomNo={session.roomNo} />
      <Hello roomNo={session.roomNo} />
      <ComingSoonEntry entry={ASK_ENTRY} />
      <MyRequests qrToken={session.qrToken} tasks={requests.data} />
      <HeroCards qrToken={session.qrToken} />
      <Services />
      <GuideList qrToken={session.qrToken} pages={home.data.infoPages} />
      <EmergencyNote />
    </main>
  );
}
