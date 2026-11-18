import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { chooseLanguage } from '../../i18n/interface-language';
import { DEFAULT_LANGUAGE, LANGUAGES, LANGUAGE_OWN_NAMES, isLanguage } from '../../i18n/languages';

export function LanguageMenu() {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const current = isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE;

  useEffect(() => {
    if (!open) return;
    function closeIfOutside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('pointerdown', closeIfOutside);
    return () => document.removeEventListener('pointerdown', closeIfOutside);
  }, [open]);

  return (
    <div
      ref={rootRef}
      className="relative flex justify-end px-5 pt-4"
      onKeyDown={(event) => {
        if (event.key === 'Escape') setOpen(false);
      }}
    >
      <button
        type="button"
        aria-label={t('account.language')}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((shown) => !shown)}
        className="flex min-h-touch items-center gap-1.5 border-none bg-transparent px-3 text-[15px] text-brand"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[22px] fill-none stroke-current stroke-[1.7]">
          <circle cx="12" cy="12" r="9" />
          <path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" />
        </svg>
        <span>{LANGUAGE_OWN_NAMES[current]}</span>
      </button>
      {open && (
        <div
          role="menu"
          aria-label={t('account.language')}
          className="absolute top-16 right-5 z-10 w-[220px] rounded-card bg-surface py-1.5 shadow-[0_12px_40px_rgba(0,0,0,.18)]"
        >
          {LANGUAGES.map((language) => (
            <button
              key={language}
              type="button"
              role="menuitemradio"
              aria-checked={language === current}
              onClick={() => {
                chooseLanguage(i18n, language);
                setOpen(false);
              }}
              className="flex min-h-touch w-full items-center gap-2 border-none bg-transparent pr-4 pl-3 text-left text-[17px]"
            >
              <span aria-hidden="true" className="flex w-[18px] text-brand">
                {language === current && (
                  <svg viewBox="0 0 24 24" className="size-3.5 fill-none stroke-current stroke-2">
                    <path d="M5 12.5l4.5 4.5L19 7.5" />
                  </svg>
                )}
              </span>
              <span>{LANGUAGE_OWN_NAMES[language]}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
