import { useTranslation } from 'react-i18next';
import { Link, Navigate, useSearchParams } from 'react-router';
import { buttonClass } from '../components/button-class';
import { CenteredNotice } from '../components/CenteredNotice';
import { requestLink } from '../lib/back-target';
import { GUEST_PATHS, guestPath } from '../paths';
import { useReadySession } from '../session';
import { useDocumentTitle } from '../use-document-title';

export function SentPage() {
  const { t } = useTranslation();
  const session = useReadySession();
  const ids = (useSearchParams()[0].get('ids') ?? '').split(',').filter(Boolean);
  useDocumentTitle(t('guest.roomName', { roomNo: session.roomNo }));

  const homePath = guestPath(session.qrToken);
  if (ids.length === 0) return <Navigate to={homePath} replace />;
  const one = ids.length === 1;
  const progress = one
    ? requestLink(session.qrToken, ids[0]!, 'sent')
    : { to: guestPath(session.qrToken, GUEST_PATHS.requests), state: undefined };
  return (
    <CenteredNotice
      icon="checkmark_circle_fill"
      tone="success"
      title={one ? t('guest.sent.one') : t('guest.sent.many', { count: ids.length })}
      body={t('guest.sent.body')}
    >
      <Link
        to={progress.to}
        state={progress.state}
        className={buttonClass('filled', { large: true, block: true })}
      >
        {t('guest.sent.view')}
      </Link>
      <Link to={homePath} className={`${buttonClass('plain')} self-center`}>
        {t('guest.sent.home')}
      </Link>
    </CenteredNotice>
  );
}
