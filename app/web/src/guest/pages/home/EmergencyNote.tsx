import { useTranslation } from 'react-i18next';

export function EmergencyNote() {
  const { t } = useTranslation();
  return (
    <p className="m-0 px-4 text-center text-guest-footnote tracking-[-.08px] text-guest-text-secondary">
      {t('guest.home.emergency')}
    </p>
  );
}
