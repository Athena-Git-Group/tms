import { LanguageMenu } from '../../components/LanguageMenu';

/** 頂列：左邊留白；右邊房號膠囊（只顯示）與語言膠囊。 */
export function TopBar({ roomNo }: { roomNo: string }) {
  return (
    <div className="flex min-h-guest-touch items-center justify-between gap-2">
      <span className="ml-auto flex items-center gap-2">
        <span className="inline-flex min-h-10 items-center gap-1.5 rounded-guest-pill bg-guest-surface px-3.5 text-[15px] leading-5 font-semibold text-guest-text tabular-nums shadow-guest-card">
          {roomNo}
        </span>
        <LanguageMenu />
      </span>
    </div>
  );
}
