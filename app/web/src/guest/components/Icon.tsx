import { ICON_PATHS, type IconName } from './icon-paths';

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
