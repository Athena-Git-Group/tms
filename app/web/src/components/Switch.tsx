export function Switch({
  checked,
  disabled,
  labelledBy,
  describedBy,
  onChange,
}: {
  checked: boolean;
  disabled?: boolean;
  labelledBy: string;
  describedBy?: string;
  onChange: (checked: boolean) => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className="group flex min-h-touch shrink-0 items-center border-none bg-transparent p-0 disabled:opacity-60"
    >
      <span className="flex h-[31px] w-[51px] items-center rounded-full bg-line-soft p-0.5 transition-colors group-aria-checked:bg-brand">
        <span className="size-[27px] rounded-full bg-white shadow-[0_3px_8px_rgba(0,0,0,0.15)] transition-transform group-aria-checked:translate-x-5" />
      </span>
    </button>
  );
}
