import { useTranslation } from 'react-i18next';
import { ComingSoonTag } from '../../components/ComingSoonTag';
import { Icon } from '../../components/Icon';
import type { IconName } from '../../components/icon-paths';
import { Illustration, type Tone } from './Illustration';

/**
 * 首頁上還沒開放的四個入口：提問列與「服務」三列。都是純文字、沒有箭頭、點了什麼都不發生；
 * 之後哪一個開放，從這張表拿掉它再畫成真的入口。
 */
export const ASK_ENTRY = { placement: 'ask', text: 'guest.home.ask', icon: 'chat_bubble_2' } as const;

export const SERVICE_ENTRIES = [
  {
    placement: 'service',
    first: 'guest.home.lateCheckout.first',
    second: 'guest.home.lateCheckout.second',
    tone: 'peach',
    icon: 'clock',
  },
  {
    placement: 'service',
    first: 'guest.home.luggage.first',
    second: 'guest.home.luggage.second',
    tone: 'sand',
    icon: 'briefcase',
  },
  {
    placement: 'service',
    first: 'guest.home.taxi.first',
    second: 'guest.home.taxi.second',
    tone: 'sky',
    icon: 'car',
  },
] as const satisfies readonly ServiceEntry[];

type AskEntry = typeof ASK_ENTRY;

interface ServiceEntry {
  placement: 'service';
  first: string;
  second: string;
  tone: Tone;
  icon: IconName;
}

export function ComingSoonEntry({ entry }: { entry: AskEntry | (typeof SERVICE_ENTRIES)[number] }) {
  const { t } = useTranslation();
  if (entry.placement === 'ask') {
    return (
      <div className="flex min-h-14 w-full items-center gap-3 rounded-guest-pill bg-guest-surface pr-3 pl-[18px] text-[17px] leading-[22px] text-guest-text-secondary shadow-guest-float">
        <Icon name={entry.icon} className="text-[22px] text-guest-text" />
        <span className="min-w-0 flex-1 truncate">{t(entry.text)}</span>
        <ComingSoonTag />
      </div>
    );
  }
  return (
    <div className="flex min-h-[84px] items-center gap-3 rounded-guest-xl bg-guest-surface pr-4 pl-3 text-guest-text shadow-guest-card">
      <Illustration tone={entry.tone} icon={entry.icon} size="row" />
      <span className="flex min-w-0 flex-1 flex-col items-start gap-1">
        <span className="text-[21px] leading-[26px] font-semibold tracking-[-.2px] [overflow-wrap:anywhere]">
          {t(entry.first)} <em className="text-guest-brand-primary not-italic">{t(entry.second)}</em>
        </span>
        <ComingSoonTag />
      </span>
    </div>
  );
}
