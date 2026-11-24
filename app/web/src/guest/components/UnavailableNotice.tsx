import { useTranslation } from 'react-i18next';
import { CenteredNotice } from './CenteredNotice';
import { useDocumentTitle } from '../use-document-title';
import { Icon } from './Icon';

/** 空房、代碼作廢、代碼不存在都是這一頁，看不出原因；聯絡櫃檯那張卡只是說明，不是電話連結。 */
export function UnavailableNotice() {
  const { t } = useTranslation();
  const title = t('guest.unavailable.title');
  useDocumentTitle(title);
  return (
    <CenteredNotice icon="info_circle_fill" tone="caution" title={title} body={t('guest.unavailable.body')}>
      <div className="grid min-h-16 grid-cols-[40px_minmax(0,1fr)] items-center gap-x-3 rounded-guest-md bg-guest-surface px-4 py-3 text-left">
        <span className="flex size-10 items-center justify-center rounded-full bg-guest-brand-container text-[24px] text-guest-brand-on-container-strong">
          <Icon name="phone" />
        </span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="text-guest-footnote text-guest-text-secondary">{t('guest.unavailable.frontDesk')}</span>
          <span className="text-guest-body font-semibold [overflow-wrap:anywhere]">{t('guest.unavailable.dial')}</span>
        </span>
      </div>
    </CenteredNotice>
  );
}
