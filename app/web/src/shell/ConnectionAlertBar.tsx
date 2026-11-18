import { useTranslation } from 'react-i18next';

/**
 * 工具列下方、內容區上緣的「即時連線異常」提醒列，照雛形 `ui/desk.css` 的 `.conn-bar`。
 * 只負責告知，上面沒有任何按鈕；出現與消失由外框決定。
 */
export function ConnectionAlertBar() {
  const { t } = useTranslation();
  return (
    <div
      role="status"
      className="mx-gutter mb-2 flex min-h-alert-bar shrink-0 items-center gap-3 rounded-card bg-surface px-4 py-2"
    >
      <svg
        viewBox="0 0 24 24"
        aria-hidden="true"
        className="size-[22px] shrink-0 fill-none stroke-[#FF9500] stroke-[1.8]"
      >
        <path d="M12 4l9 16H3z" />
        <path d="M12 10v4M12 17v.5" />
      </svg>
      <b className="text-[15px] font-semibold">{t('shell.connectionAlert.title')}</b>
      <span className="text-[13px] text-text-2">{t('shell.connectionAlert.description')}</span>
    </div>
  );
}
