import { useTranslation } from 'react-i18next';
import { CenteredNotice } from './CenteredNotice';

export function LoadFailed({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation();
  return (
    <CenteredNotice
      icon={
        <>
          <path d="M12 4l9 16H3z" />
          <path d="M12 10v4M12 17v.5" />
        </>
      }
      title={t('loadFailed.title')}
      hint={t('loadFailed.hint')}
    >
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 min-h-touch rounded-full border-[1.5px] border-brand bg-transparent px-[22px] text-base font-semibold text-brand"
      >
        {t('loadFailed.retry')}
      </button>
    </CenteredNotice>
  );
}
