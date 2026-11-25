export interface SegmentedOption<T extends string> {
  value: T;
  label: string;
}

export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
  disabled,
}: {
  label: string;
  options: readonly SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  disabled?: boolean;
}) {
  return (
    <div role="group" aria-label={label} className="inline-flex rounded-full bg-fill p-0.5">
      {options.map((option) => {
        const pressed = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={pressed}
            disabled={disabled}
            onClick={() => onChange(option.value)}
            className={`min-h-touch min-w-touch rounded-full border-none px-4 text-[15px] ${
              pressed
                ? 'bg-surface font-semibold text-text shadow-[0_1px_4px_rgba(0,0,0,.12)]'
                : 'bg-transparent text-text'
            }`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
