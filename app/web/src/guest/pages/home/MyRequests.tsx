import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { GuestTask } from '../../api/types';
import { Icon } from '../../components/Icon';
import { ProgressCard } from '../../components/ProgressCard';
import { requestLink } from '../../lib/back-target';
import { homeRequestsSection } from '../../lib/stage';
import { GUEST_PATHS, guestPath } from '../../paths';
import { readSentOrder } from '../../sent-order';

/** 首頁「我的需求」：這次住宿一張都沒送過時整塊不出現。 */
export function MyRequests({ qrToken, tasks }: { qrToken: string; tasks: GuestTask[] }) {
  const { t } = useTranslation();
  const section = homeRequestsSection(tasks, readSentOrder(qrToken));
  if (section.kind === 'none') return null;
  const listPath = guestPath(qrToken, GUEST_PATHS.requests);
  return (
    <section className="flex flex-col gap-2.5">
      <div className="flex items-center justify-between gap-2">
        <h2 className="m-0 text-guest-section-title font-bold tracking-[.38px]">{t('guest.requests.title')}</h2>
        <Link
          to={listPath}
          className="-mr-1 inline-flex min-h-guest-touch items-center border-[1.5px] border-transparent px-1 text-[15px] leading-5 font-semibold tracking-[-.43px] whitespace-nowrap text-guest-brand-primary no-underline active:opacity-60"
        >
          {t('guest.myRequests.all', { count: section.count })}
        </Link>
      </div>
      {section.kind === 'card' ? (
        <ProgressCard
          task={section.task}
          link={requestLink(qrToken, section.task.id, 'home')}
        />
      ) : (
        <Link
          to={listPath}
          className="flex min-h-16 items-center gap-3 rounded-guest-xl bg-guest-surface px-4 text-guest-text no-underline shadow-guest-card active:scale-[.98]"
        >
          <span className="flex min-w-0 flex-1 flex-col items-start gap-1">
            <span className="text-guest-headline font-semibold [overflow-wrap:anywhere]">{t('guest.myRequests.noActive')}</span>
            <span className="text-guest-footnote text-guest-text-secondary">{t('guest.myRequests.history')}</span>
          </span>
          <Icon name="chevron_forward" className="text-[16px] text-guest-text-placeholder" />
        </Link>
      )}
    </section>
  );
}
