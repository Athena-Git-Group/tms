import { useRef, useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { LANGUAGE_OWN_NAMES, LANGUAGES, type Language } from '../../i18n/languages';
import { useSetGuestLanguage } from '../use-guest-language';
import { Icon } from './Icon';

/**
 * 首頁頂列的語言膠囊與選單：膠囊看得到的是目前語言的短寫法，給報讀軟體的名稱是這個語言的「語言」。
 * 選單列出四種語言各自的寫法，目前的加粗打勾；點選單外、Esc 都收起，焦點回到膠囊。
 */
export function LanguageMenu() {
  const { t, i18n } = useTranslation();
  const setLanguage = useSetGuestLanguage();
  const [open, setOpen] = useState(false);
  const capsule = useRef<HTMLButtonElement>(null);

  const close = () => {
    setOpen(false);
    capsule.current?.focus();
  };
  const pick = (language: Language) => {
    setLanguage(language);
    close();
  };
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Escape') return;
    event.preventDefault();
    close();
  };

  return (
    <span className="relative inline-flex" onKeyDown={onKeyDown}>
      <button
        ref={capsule}
        type="button"
        aria-label={t('guest.language')}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((shown) => !shown)}
        className="inline-flex min-h-guest-touch cursor-pointer items-center gap-1 rounded-guest-pill border-0 bg-guest-fill px-3 text-[15px] leading-5 font-semibold tracking-[-.23px] text-guest-text"
      >
        <Icon name="globe" className="text-[18px] text-guest-brand-primary" />
        <span>{t('guest.languageShort')}</span>
        <Icon name="chevron_forward" className="rotate-90 text-[12px] text-guest-text-secondary" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={close} />
          <div
            role="menu"
            aria-label={t('guest.language')}
            className="absolute top-[calc(100%+8px)] right-0 z-41 min-w-[184px] overflow-hidden rounded-guest-lg bg-guest-surface-raised text-left shadow-guest-popover motion-safe:animate-guest-pop"
          >
            <p className="m-0 px-4 pt-3 pb-1 text-guest-footnote font-semibold text-guest-text-secondary">
              {t('guest.language')}
            </p>
            {LANGUAGES.map((language) => {
              const current = language === i18n.language;
              return (
                <button
                  key={language}
                  type="button"
                  role="menuitemradio"
                  aria-checked={current}
                  lang={language}
                  onClick={() => pick(language)}
                  className={`flex min-h-12 w-full cursor-pointer items-center gap-3 border-0 bg-transparent px-4 text-left text-guest-body tracking-normal text-guest-text [&+&]:shadow-[inset_0_.5px_0_var(--color-guest-separator)] ${
                    current ? 'font-semibold' : 'font-normal'
                  }`}
                >
                  <span className="min-w-0 flex-auto">{LANGUAGE_OWN_NAMES[language]}</span>
                  <Icon
                    name="checkmark"
                    className={`text-[16px] text-guest-brand-primary ${current ? 'visible' : 'invisible'}`}
                  />
                </button>
              );
            })}
          </div>
        </>
      )}
    </span>
  );
}
