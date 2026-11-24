import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Icon } from '../../components/Icon';
import type { IconName } from '../../components/icon-paths';
import { GUEST_PATHS, guestPath } from '../../paths';
import { Illustration, TONES, type Tone } from './Illustration';

/** 兩張主視覺卡：備品補充與設備報修。 */
export function HeroCards({ qrToken }: { qrToken: string }) {
  const { t } = useTranslation();
  return (
    <div className="grid grid-cols-2 gap-2.5">
      <HeroCard
        to={guestPath(qrToken, GUEST_PATHS.supply)}
        tone="peach"
        icon="cube_box"
        title={t('guest.home.supplyTitle')}
        lead={t('guest.home.supplyLead')}
        action={t('guest.supply.title')}
      />
      <HeroCard
        to={guestPath(qrToken, GUEST_PATHS.repair)}
        tone="teal"
        icon="hammer"
        title={t('guest.home.repairTitle')}
        lead={t('guest.home.repairLead')}
        action={t('guest.repair.title')}
      />
    </div>
  );
}

/** 整張卡是一個連結，名稱就是底列要去的地方。 */
function HeroCard({
  to,
  tone,
  icon,
  title,
  lead,
  action,
}: {
  to: string;
  tone: Tone;
  icon: IconName;
  title: string;
  lead: string;
  action: string;
}) {
  return (
    <Link
      to={to}
      aria-label={action}
      className={`relative flex min-h-[220px] flex-col justify-between gap-2 overflow-hidden rounded-guest-xl p-4 no-underline shadow-guest-card after:pointer-events-none after:absolute after:-right-10 after:-bottom-[60px] after:size-[150px] after:rounded-full after:opacity-16 after:content-[''] active:scale-[.98] ${TONES[tone].card}`}
    >
      <span className="relative z-[1] text-guest-hero-title font-bold tracking-[-.2px] [overflow-wrap:anywhere]">
        {title}
        <em className="block not-italic">{lead}</em>
      </span>
      <Illustration tone={tone} icon={icon} size="hero" />
      <span className="relative z-[1] flex items-center gap-1.5 text-[15px] leading-5 font-semibold">
        <span className="min-w-0 flex-1">{action}</span>
        <Icon name="chevron_forward" className="text-[16px]" />
      </span>
    </Link>
  );
}
