const BASE =
  'inline-flex min-w-guest-touch cursor-pointer items-center justify-center gap-1.5 rounded-guest-pill border-[1.5px] border-transparent text-[17px] leading-[22px] font-semibold tracking-[-.43px] whitespace-nowrap no-underline transition-opacity duration-[120ms] active:not-disabled:opacity-60 disabled:cursor-default disabled:opacity-38 aria-busy:pointer-events-none aria-busy:opacity-60 aria-busy:before:size-[18px] aria-busy:before:animate-spin aria-busy:before:rounded-full aria-busy:before:border-2 aria-busy:before:border-current aria-busy:before:border-r-transparent aria-busy:before:content-[""] [&_svg]:text-[20px]';

const VARIANTS = {
  filled: 'bg-guest-brand-primary text-guest-brand-on-primary',
  plain: 'bg-transparent text-guest-brand-primary',
  gray: 'bg-guest-fill-button text-guest-brand-primary',
  grayDanger: 'bg-guest-fill-button text-guest-danger',
} as const;

/**
 * 住客頁的膠囊按鈕：實心、文字、灰底、灰底紅字四種，大的高 54 點；`tight` 給並排平分寬度的按鈕（確認框）。
 * 高度與左右內距各只給一個類別：同一屬性給兩個類別時，誰生效看產生的 CSS 先後，不看這裡寫的先後。
 */
export function buttonClass(
  variant: keyof typeof VARIANTS,
  { large = false, block = false, tight = false } = {},
): string {
  const padding = tight ? 'px-3' : variant === 'plain' ? 'px-2' : large ? 'px-6' : 'px-5';
  return [
    BASE,
    VARIANTS[variant],
    padding,
    large ? 'min-h-guest-bar-button' : 'min-h-guest-touch',
    block ? 'flex w-full' : '',
  ].join(' ');
}
