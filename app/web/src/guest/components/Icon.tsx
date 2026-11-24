import { ICON_PATHS, type IconName } from './icon-paths';

/** 圖示一律是裝飾，大小跟著字級（1em），顏色跟著字色；要讓報讀軟體知道的意思放在旁邊的字或按鈕名稱。 */
export function Icon({ name, className = '' }: { name: IconName; className?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={`inline-block size-[1em] shrink-0 fill-current align-middle ${className}`}
    >
      <path d={ICON_PATHS[name]} />
    </svg>
  );
}
