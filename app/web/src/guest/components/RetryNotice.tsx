import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '../use-document-title';
import { buttonClass } from './button-class';
import { CenteredNotice } from './CenteredNotice';

export function RetryNotice({ retrying, onRetry }: { retrying: boolean; onRetry: () => void }) {
  const { t } = useTranslation();
  const title = t('guest.connectionFailed');
  useDocumentTitle(title);
  return (
    <CenteredNotice icon="wifi" tone="neutral" title={title}>
      <button
        type="button"
        className={buttonClass('filled', { large: true, block: true })}
        disabled={retrying}
        aria-busy={retrying || undefined}
        onClick={onRetry}
      >
        {t('guest.retry')}
      </button>
    </CenteredNotice>
  );
}
