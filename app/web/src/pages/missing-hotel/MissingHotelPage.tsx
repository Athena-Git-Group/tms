import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { LanguageMenu } from '../../features/account';
import { HOTEL_CODE_MAX_LENGTH, isWellFormedHotelCode, normalizeHotelCode } from '../../lib/hotel-code';
import { PATHS, hotelPath } from '../../lib/paths';

export function MissingHotelPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [input, setInput] = useState('');
  const code = normalizeHotelCode(input);
  const canGo = isWellFormedHotelCode(code);

  function go(event: FormEvent) {
    event.preventDefault();
    if (!canGo) return;
    navigate(hotelPath(code, PATHS.login));
  }

  return (
    <main data-page="missing-hotel" className="flex min-h-screen min-w-shell flex-col bg-bg font-sans text-text">
      <LanguageMenu />
      <div className="flex flex-auto justify-center px-6 pt-6 pb-12">
        <form className="my-auto flex w-full max-w-auth-form flex-col gap-[22px]" noValidate onSubmit={go}>
          <div className="flex flex-col items-center gap-1.5 text-center">
            <h1 className="m-0 text-[28px] leading-[1.2] font-bold">{t('missingHotel.title')}</h1>
            <div className="text-[15px] leading-[1.45] text-text-2">{t('missingHotel.body')}</div>
          </div>

          <div className="overflow-hidden rounded-card bg-surface">
            <div className="flex min-h-[52px] items-center gap-2 pr-1.5 pl-4">
              <label htmlFor="hotel-code" className="w-form-label shrink-0 text-[17px] leading-[1.25]">
                {t('missingHotel.codeLabel')}
              </label>
              <input
                id="hotel-code"
                type="text"
                autoComplete="off"
                autoCapitalize="none"
                spellCheck={false}
                maxLength={HOTEL_CODE_MAX_LENGTH + 8}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                className="min-h-touch min-w-0 flex-auto border-none bg-transparent p-0 text-[17px] outline-none"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={!canGo}
            className="min-h-[54px] w-full rounded-full border-none bg-brand text-[17px] font-semibold text-white disabled:bg-line"
          >
            {t('missingHotel.go')}
          </button>
        </form>
      </div>
    </main>
  );
}
