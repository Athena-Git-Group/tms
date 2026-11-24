import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { stepView, type StepLook } from '../lib/stage';
import { Icon } from './Icon';

const DOT: Record<StepLook, string> = {
  done: 'border-guest-brand-primary bg-guest-brand-primary',
  current: 'border-guest-brand-primary bg-guest-brand-primary shadow-[inset_0_0_0_4px_var(--color-guest-surface)]',
  todo: 'border-guest-separator-opaque bg-guest-surface',
};

const TITLE: Record<StepLook, string> = {
  done: 'text-guest-text',
  current: 'text-guest-brand-primary',
  todo: 'text-guest-text-secondary',
};

export function FourSteps({ task }: { task: GuestTask }) {
  const { t } = useTranslation();
  const steps = stepView(task);
  if (!steps) return null;
  return (
    <ol aria-label={t('guest.request.title')} className="m-0 flex list-none flex-col rounded-guest-md bg-guest-surface p-4">
      {steps.map((step, index) => (
        <li
          key={step.titleKey}
          aria-current={step.look === 'current' ? 'step' : undefined}
          className="group grid grid-cols-[24px_minmax(0,1fr)] gap-x-3"
        >
          <span className="flex flex-col items-center">
            <span
              className={`flex size-6 shrink-0 items-center justify-center rounded-full border-2 text-[14px] text-guest-brand-on-primary ${DOT[step.look]}`}
            >
              {step.look === 'done' ? <Icon name="checkmark" /> : null}
            </span>
            {index < steps.length - 1 ? (
              <span
                className={`my-0.5 min-h-4 w-0.5 flex-1 ${step.look === 'done' ? 'bg-guest-brand-primary' : 'bg-guest-separator-opaque'}`}
              />
            ) : null}
          </span>
          <span className="flex min-w-0 flex-col gap-0.5 pt-px pb-[18px] group-last:pb-0">
            <span className={`text-guest-headline font-semibold tracking-[-.43px] ${TITLE[step.look]}`}>
              {t(step.titleKey)}
            </span>
            {step.sub ? (
              <span className="text-guest-footnote text-guest-text-secondary">
                {t(step.sub.key, { dept: t(step.sub.deptKey) })}
              </span>
            ) : null}
          </span>
        </li>
      ))}
    </ol>
  );
}
