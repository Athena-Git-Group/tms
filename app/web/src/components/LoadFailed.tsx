import { useTranslation } from 'react-i18next';
import { CenteredNotice } from './CenteredNotice';

/**
 * 第一次就讀不到資料時，內容區置中顯示的訊息與「重試」。網路恢復後不按也會由外框每 5 秒自動再讀，讀到就換回正常畫面。
 * 總覽看板與待派單頁共用同一個樣子與同一句說明，所以不帶頁面專屬的內容。
 */
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
