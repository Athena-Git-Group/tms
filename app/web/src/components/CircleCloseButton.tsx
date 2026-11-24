import { forwardRef } from 'react';

export const CircleCloseButton = forwardRef<
  HTMLButtonElement,
  { label: string; onClick: () => void; disabled?: boolean }
>(function CircleCloseButton({ label, onClick, disabled }, ref) {
  return (
    <button
      ref={ref}
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="flex size-touch shrink-0 items-center justify-center rounded-full border border-solid border-white/80 bg-white/70 p-0 text-[#1C1C1E] shadow-[0_2px_10px_rgba(0,0,0,.08)] disabled:opacity-60"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[15px] fill-none stroke-current stroke-[2.4]">
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
  );
});
