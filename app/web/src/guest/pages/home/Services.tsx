import { useTranslation } from 'react-i18next';
import { ComingSoonEntry, SERVICE_ENTRIES } from './ComingSoon';

export function Services() {
  const { t } = useTranslation();
  return (
    <section className="flex flex-col gap-2">
      <h2 className="m-0 text-guest-section-title font-bold tracking-[.38px]">{t('guest.home.services')}</h2>
      <div className="flex flex-col gap-2.5">
        {SERVICE_ENTRIES.map((entry) => (
          <ComingSoonEntry key={entry.first} entry={entry} />
        ))}
      </div>
    </section>
  );
}
