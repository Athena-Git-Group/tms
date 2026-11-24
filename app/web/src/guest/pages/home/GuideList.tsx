import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { GuestInfoPage } from '../../api/types';
import { Icon } from '../../components/Icon';
import { listableInfoPages } from '../../lib/info-pages';
import { guestPath } from '../../paths';

/** 「住宿指南」：每一頁一列，只有標題與箭頭，順序照後端；一頁都不能列時整段不出現。 */
export function GuideList({ qrToken, pages }: { qrToken: string; pages: readonly GuestInfoPage[] }) {
  const { t } = useTranslation();
  const listed = listableInfoPages(pages);
  if (listed.length === 0) return null;
  return (
    <section className="flex flex-col gap-2">
      <h2 className="m-0 text-guest-section-title font-bold tracking-[.38px]">{t('guest.home.guide')}</h2>
      <div className="flex flex-col overflow-hidden rounded-guest-lg bg-guest-surface shadow-guest-card">
        {listed.map((page) => (
          <Link
            key={page.key}
            to={guestPath(qrToken, `guide/${encodeURIComponent(page.key)}`)}
            className="flex min-h-16 w-full items-center gap-3 px-4 py-2.5 text-guest-text no-underline [&+&]:shadow-[inset_0_.5px_0_var(--color-guest-separator)]"
          >
            <span className="min-w-0 flex-1 text-[17px] leading-[22px] font-semibold [overflow-wrap:anywhere]">
              {page.title}
            </span>
            <Icon name="chevron_forward" className="text-[16px] text-guest-text-placeholder" />
          </Link>
        ))}
      </div>
    </section>
  );
}
