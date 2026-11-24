import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DEFAULT_LANGUAGE, isLanguage } from '../../i18n/languages';
import { createRequest } from '../api/client';
import { BottomBar } from '../components/BottomBar';
import { buttonClass } from '../components/button-class';
import { InlineNotice } from '../components/InlineNotice';
import { TitleBar } from '../components/TitleBar';
import {
  NOTE_MAX,
  REPAIR_CATEGORIES,
  canSubmitRepair,
  clampNote,
  composeRepairText,
  noteLength,
  type RepairCategory,
} from '../lib/repair-text';
import { useGuestQuota } from '../queries';
import { useReadySession } from '../session';
import { useGuestSubmit } from '../use-guest-submit';
import { useQuotaGuard } from '../use-quota-guard';

export function RepairPage() {
  const { t, i18n } = useTranslation();
  const session = useReadySession();
  const [category, setCategory] = useState<RepairCategory | null>(null);
  const [note, setNote] = useState('');
  const { sending, submit } = useGuestSubmit();
  const guard = useQuotaGuard(useGuestQuota(session.qrToken));

  const noteRequired = category?.id === 'other';

  const send = async () => {
    if (category === null || sending || !guard.check(1)) return;
    const lang = isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE;
    guard.report(
      await submit([composeRepairText(category, note)], (text, sessionToken, key) => createRequest(sessionToken, { text, lang }, key)),
    );
  };

  return (
    <>
      <TitleBar title={t('guest.repair.title')} />
      <main className="flex min-h-[calc(100dvh-var(--spacing-guest-title-bar))] flex-col gap-6 pt-2">
        <section className="flex flex-col gap-2">
          <h2 id="repair-what" className="m-0 text-guest-section-title font-bold tracking-[.38px]">
            {t('guest.repair.what')}
          </h2>
          <div role="radiogroup" aria-labelledby="repair-what" className="flex flex-wrap gap-2">
            {REPAIR_CATEGORIES.map((each) => (
              <button
                key={each.id}
                type="button"
                role="radio"
                aria-checked={category?.id === each.id}
                disabled={sending}
                onClick={() => {
                  guard.dismiss();
                  setCategory(each);
                }}
                className="inline-flex min-h-guest-touch cursor-pointer items-center rounded-guest-pill border border-guest-separator-opaque bg-guest-surface px-3.5 text-[15px] leading-5 font-semibold tracking-normal whitespace-nowrap text-guest-text disabled:cursor-default disabled:opacity-38 aria-checked:border-guest-brand-primary aria-checked:bg-guest-brand-primary aria-checked:text-guest-brand-on-primary"
              >
                {t(`guest.repair.category.${each.id}`)}
              </button>
            ))}
          </div>
        </section>
        <label className="flex flex-col gap-1.5 rounded-guest-md border-[1.5px] border-transparent bg-guest-surface px-4 py-3 focus-within:border-guest-brand-primary">
          <span className="text-guest-footnote font-semibold tracking-[-.08px] text-guest-text-secondary">
            {t(noteRequired ? 'guest.repair.noteRequired' : 'guest.repair.noteOptional')}
          </span>
          <textarea
            value={note}
            onChange={(event) => {
              guard.dismiss();
              setNote(clampNote(event.target.value));
            }}
            disabled={sending}
            placeholder={t('guest.repair.notePlaceholder')}
            className="min-h-[72px] w-full resize-none border-0 bg-transparent p-0 text-[17px] leading-6 text-guest-text outline-none [field-sizing:content] placeholder:text-guest-text-placeholder"
          />
          <span className="flex justify-between gap-2 text-guest-caption text-guest-text-secondary tabular-nums">
            <span>{t('guest.repair.noteHint')}</span>
            <span>{t('guest.repair.noteCount', { count: noteLength(note), max: NOTE_MAX })}</span>
          </span>
        </label>
        {guard.notice ? <InlineNotice notice={guard.notice} /> : null}
        <div className="flex-auto" />
        <BottomBar>
          <button
            type="button"
            disabled={!canSubmitRepair(category, note) || sending}
            aria-busy={sending || undefined}
            onClick={send}
            className={buttonClass('filled', { large: true })}
          >
            {t('guest.repair.submit')}
          </button>
        </BottomBar>
      </main>
    </>
  );
}
