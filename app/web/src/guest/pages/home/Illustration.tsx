import { Icon } from '../../components/Icon';
import type { IconName } from '../../components/icon-paths';

/** 插圖的四種配色：卡片的底色、字色與角落大圓，以及插圖方塊的顏色。 */
export const TONES = {
  peach: {
    card: 'bg-guest-brand-accent-container text-guest-brand-on-accent-container after:bg-guest-illo-peach',
    token: 'bg-guest-illo-peach',
  },
  teal: {
    card: 'bg-guest-brand-container text-guest-brand-on-container-strong after:bg-guest-illo-teal',
    token: 'bg-guest-illo-teal',
  },
  sky: { card: '', token: 'bg-guest-illo-sky' },
  sand: { card: '', token: 'bg-guest-illo-sand' },
} as const;

export type Tone = keyof typeof TONES;

const SIZES = {
  hero: {
    frame: 'absolute right-3 bottom-11 size-[88px]',
    token: 'size-[86%] rotate-[10deg] text-[40.48px]',
  },
  row: { frame: 'relative size-[60px]', token: 'size-[80%] text-[25.2px]' },
} as const;

/** 圓角方塊裡一個白色圖示；只是裝飾，不給報讀軟體。 */
export function Illustration({ tone, icon, size }: { tone: Tone; icon: IconName; size: keyof typeof SIZES }) {
  return (
    <span aria-hidden="true" className={`inline-flex shrink-0 items-center justify-center ${SIZES[size].frame}`}>
      <span
        className={`relative flex items-center justify-center rounded-[28%] text-guest-on-illo ${SIZES[size].token} ${TONES[tone].token}`}
      >
        <Icon name={icon} />
      </span>
    </span>
  );
}
