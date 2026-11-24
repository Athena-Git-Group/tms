import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { canRate } from '../lib/stage';
import { buttonClass } from './button-class';
import { Icon } from './Icon';

const WORDS = [
  'guest.rating.veryPoor',
  'guest.rating.poor',
  'guest.rating.okay',
  'guest.rating.good',
  'guest.rating.excellent',
] as const;

export function RatingCard({
  task,
  sending,
  onSubmit,
}: {
  task: GuestTask;
  sending: boolean;
  onSubmit: (stars: number) => void;
}) {
  const { t } = useTranslation();
  const [stars, setStars] = useState(0);
  if (!canRate(task)) return null;
  return (
    <section className="flex flex-col gap-3 rounded-guest-lg bg-guest-surface p-4 shadow-guest-card">
      <h2 className="m-0 text-center text-guest-section-title font-bold tracking-[.38px]">{t('guest.rating.question')}</h2>
      <div className="flex flex-col items-center gap-2">
        <div role="radiogroup" aria-label={t('guest.rating.group')} className="flex gap-1">
          {[1, 2, 3, 4, 5].map((k) => (
            <button
              key={k}
              type="button"
              role="radio"
              aria-checked={k === stars}
              aria-label={t('guest.rating.star', { n: k })}
              disabled={sending}
              onClick={() => setStars(k)}
              className={`inline-flex size-guest-touch cursor-pointer items-center justify-center rounded-full border-0 bg-transparent p-0 text-[34px] ${
                k <= stars ? 'text-guest-rating-star' : 'text-guest-text-placeholder'
              }`}
            >
              <Icon name={k <= stars ? 'star_fill' : 'star'} />
            </button>
          ))}
        </div>
        <span aria-live="polite" className="min-h-[22px] text-guest-headline font-semibold">
          {stars ? t(WORDS[stars - 1]!) : t('guest.rating.hint')}
        </span>
      </div>
      <button
        type="button"
        className={buttonClass('filled', { block: true })}
        disabled={stars === 0 || sending}
        aria-busy={sending || undefined}
        onClick={() => onSubmit(stars)}
      >
        {t('guest.rating.submit')}
      </button>
    </section>
  );
}
