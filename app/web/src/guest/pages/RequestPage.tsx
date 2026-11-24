import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useParams } from 'react-router';
import { cancelRequest, rateRequest } from '../api/client';
import { CancelDialog } from '../components/CancelDialog';
import { FourSteps } from '../components/FourSteps';
import { MessageBox } from '../components/MessageBox';
import { PageLoading } from '../components/PageLoading';
import { RatingCard } from '../components/RatingCard';
import { RequestBottom } from '../components/RequestBottom';
import { RequestCard } from '../components/RequestCard';
import { TitleBar } from '../components/TitleBar';
import { backTargetOf, cameFrom } from '../lib/back-target';
import { GUEST_PATHS, guestPath } from '../paths';
import { useGuestRequests } from '../queries';
import { useReadySession } from '../session';
import { useGuestMutation } from '../use-guest-mutation';

export function RequestPage() {
  const { t } = useTranslation();
  const session = useReadySession();
  const requests = useGuestRequests(session.qrToken);
  const id = useParams().id ?? '';
  const listPath = guestPath(session.qrToken, GUEST_PATHS.requests);
  const backTo = backTargetOf(cameFrom(useLocation().state), session.qrToken);
  const [confirming, setConfirming] = useState(false);
  const cancelButton = useRef<HTMLButtonElement>(null);
  const notSent = t('guest.toast.notSent');
  const cancel = useGuestMutation<string>({
    send: (sessionToken, taskId, key) => cancelRequest(sessionToken, taskId, key),
    succeeded: t('guest.toast.cancelled'),
    refused: t('guest.toast.tooLate'),
    notSent,
  });
  const rate = useGuestMutation<{ taskId: string; stars: number }>({
    send: (sessionToken, { taskId, stars }, key) => rateRequest(sessionToken, taskId, stars, key),
    succeeded: t('guest.toast.thanks'),
    refused: null,
    notSent,
  });

  if (!requests.data) return <PageLoading />;
  const task = requests.data.find((each) => each.id === id);
  if (!task) return <Navigate to={listPath} replace />;

  const closeDialog = () => {
    setConfirming(false);
    cancelButton.current?.focus();
  };

  const confirmCancel = () => {
    setConfirming(false);
    void cancel.run(task.id);
  };

  return (
    <>
      <TitleBar title={t('guest.request.title')} backTo={backTo} />
      <main className="flex min-h-[calc(100dvh-var(--spacing-guest-title-bar))] flex-col gap-6 pt-2 pb-[calc(48px+env(safe-area-inset-bottom))]">
        <RequestCard task={task} />
        <MessageBox task={task} />
        <FourSteps task={task} />
        <RatingCard
          task={task}
          sending={rate.busy}
          onSubmit={(stars) => void rate.run({ taskId: task.id, stars })}
        />
        <RequestBottom ref={cancelButton} task={task} cancelling={cancel.busy} onCancel={() => setConfirming(true)} />
      </main>
      {confirming && <CancelDialog title={task.title ?? ''} onKeep={closeDialog} onConfirm={confirmCancel} />}
    </>
  );
}
