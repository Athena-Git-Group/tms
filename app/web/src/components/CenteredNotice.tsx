import type { ReactNode } from 'react';

export function CenteredNotice({
  icon,
  iconClassName,
  title,
  hint,
  children,
}: {
  icon: ReactNode;
  iconClassName?: string;
  title: string;
  hint: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex min-h-[280px] flex-auto flex-col items-center justify-center px-8 pt-6 pb-10 text-center">
      <svg
        viewBox="0 0 24 24"
        aria-hidden="true"
        className={`size-14 fill-none stroke-current stroke-[1.3] ${iconClassName ?? 'text-text-4'}`}
      >
        {icon}
      </svg>
      <b className="mt-3.5 text-[22px] leading-7">{title}</b>
      <span className="mt-1.5 text-[15px] leading-5 text-text-2">{hint}</span>
      {children}
    </div>
  );
}
