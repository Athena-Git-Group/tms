import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '../use-document-title';
import { buttonClass } from './button-class';
import { CenteredNotice } from './CenteredNotice';

/** 被限流、連不上、後端出錯時的重試狀態；沒有「請洽櫃檯」。按了之後等後端回覆時按鈕鎖住、轉圈，字不變。 */
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
