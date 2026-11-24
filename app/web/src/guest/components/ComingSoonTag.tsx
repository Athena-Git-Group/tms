import { useTranslation } from 'react-i18next';

export function ComingSoonTag() {
  const { t } = useTranslation();
  return (
    <span className="inline-flex min-h-6 shrink-0 items-center gap-1 rounded-guest-pill bg-guest-status-neutral-container px-2.5 py-0.5 text-guest-footnote font-semibold tracking-[-.08px] whitespace-nowrap text-guest-status-on-neutral-container">
      {t('guest.comingSoon')}
    </span>
  );
}
