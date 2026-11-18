import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Session } from '../../stores/session';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { chooseLanguage } from '../../i18n/interface-language';
import { DEFAULT_LANGUAGE, LANGUAGES, LANGUAGE_OWN_NAMES, isLanguage, type Language } from '../../i18n/languages';

/**
 * 帳號面板：置中、後面是半透明遮罩，點遮罩或按 Esc 等於按「完成」。
 * 內容只有三塊：身分卡（只是讓人確認登入的是自己，不能點）、「語言」、「登出」。
 * 點「語言」在同一個面板裡換成語言頁，左上的「‹ 帳號」回來。
 *
 * 按「登出」先問一次。問的時候面板先收起來，只留確認對話框；
 * 按「取消」就回到看板，不再回到面板，畫面上不留任何對話框。
 */
export function AccountPanel({
  session,
  onClose,
  onLogOut,
}: {
  session: Session;
  onClose: () => void;
  onLogOut: () => void;
}) {
  const { t, i18n } = useTranslation();
  const [view, setView] = useState<'account' | 'language'>('account');
  const [confirmingLogOut, setConfirmingLogOut] = useState(false);
  const current = isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE;

  if (confirmingLogOut) {
    return (
      <ConfirmDialog
        title={t('account.logoutConfirmTitle')}
        body={t('account.logoutConfirmBody')}
        cancelLabel={t('account.cancel')}
        confirmLabel={t('account.logout')}
        onCancel={onClose}
        onConfirm={onLogOut}
      />
    );
  }

  return (
    <div
      className="fixed inset-0 z-30 flex items-center justify-center bg-black/[.32]"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') onClose();
      }}
    >
      {/* 面板換到語言頁時標題變成「語言」，但面板本身仍叫「帳號」，所以名稱不跟著標題走。 */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('account.title')}
        className="flex h-account-panel max-h-account-panel w-account-panel flex-col overflow-hidden rounded-card bg-bg shadow-[0_20px_60px_rgba(0,0,0,.25)]"
      >
        <div className="grid min-h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center px-2 pt-1.5">
          <span>
            {view === 'language' && (
              <button
                type="button"
                onClick={() => setView('account')}
                className="inline-flex min-h-touch items-center gap-0.5 border-none bg-transparent px-2 text-[17px] text-brand"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true" className="size-3.5 fill-none stroke-current stroke-2">
                  <path d="M15 5l-7 7 7 7" />
                </svg>
                {t('account.title')}
              </button>
            )}
          </span>
          <h2 className="m-0 text-center text-[17px] font-semibold whitespace-nowrap">
            {t(view === 'account' ? 'account.title' : 'account.language')}
          </h2>
          <span className="justify-self-end">
            <button
              type="button"
              autoFocus
              onClick={onClose}
              className="inline-flex min-h-touch items-center border-none bg-transparent px-2 text-[17px] font-semibold text-brand"
            >
              {t('account.done')}
            </button>
          </span>
        </div>
        <div className="flex min-h-0 flex-auto flex-col gap-[18px] overflow-y-auto px-5 pt-2 pb-4">
          {view === 'account' ? (
            <>
              <div className="flex shrink-0 items-center gap-3.5 rounded-card bg-surface px-4 py-3.5">
                <span
                  aria-hidden="true"
                  className="flex size-14 shrink-0 items-center justify-center rounded-full bg-brand text-2xl font-extrabold text-white"
                >
                  {session.name.slice(-1)}
                </span>
                <span className="grow">
                  <b className="block text-xl">{session.name}</b>
                  <span className="mt-[3px] block text-[15px] text-text-2">
                    {t('account.identityLine', { role: t(`roles.${session.role}`), loginId: session.loginId })}
                  </span>
                  <small className="mt-[3px] block text-[13px] text-text-4">{session.hotelName}</small>
                </span>
              </div>
              <div className="shrink-0 overflow-hidden rounded-card bg-surface">
                <button
                  type="button"
                  aria-label={`${t('account.language')} ${LANGUAGE_OWN_NAMES[current]}`}
                  onClick={() => setView('language')}
                  className="flex min-h-[52px] w-full items-center gap-2.5 border-none bg-transparent px-4 py-1.5 text-left text-[17px]"
                >
                  <span className="grow">{t('account.language')}</span>
                  <span className="text-text-4">{LANGUAGE_OWN_NAMES[current]}</span>
                  <svg viewBox="0 0 24 24" aria-hidden="true" className="size-3.5 fill-none stroke-current stroke-2 text-text-4">
                    <path d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              </div>
              <div className="shrink-0 overflow-hidden rounded-card bg-surface">
                <button
                  type="button"
                  onClick={() => setConfirmingLogOut(true)}
                  className="flex min-h-[52px] w-full items-center justify-center border-none bg-transparent px-4 py-1.5 text-[17px] text-danger"
                >
                  {t('account.logout')}
                </button>
              </div>
            </>
          ) : (
            <LanguagePage current={current} onChoose={(language) => chooseLanguage(i18n, language)} />
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * 語言頁：四個單選列，第一行是該語言自己的寫法，第二行是用目前介面語言寫的名稱，選中的右側打勾。
 * 清單下方說明換語言只換介面文字，免得同仁以為住客或同事寫的內容也會跟著翻譯。
 */
function LanguagePage({ current, onChoose }: { current: Language; onChoose: (language: Language) => void }) {
  const { t } = useTranslation();
  const sectionId = useId();

  return (
    <div className="flex shrink-0 flex-col gap-2">
      <span id={sectionId} className="px-4 text-[13px] text-text-3">
        {t('account.languageSection')}
      </span>
      <div role="radiogroup" aria-labelledby={sectionId} className="overflow-hidden rounded-card bg-surface">
        {LANGUAGES.map((language, index) => (
          <button
            key={language}
            type="button"
            role="radio"
            aria-checked={language === current}
            onClick={() => onChoose(language)}
            className={`flex min-h-[60px] w-full items-center gap-2.5 bg-transparent px-4 py-1.5 text-left text-[17px] ${
              index > 0 ? 'border-t-[0.5px] border-line-soft' : ''
            }`}
          >
            <span className="grow">
              {LANGUAGE_OWN_NAMES[language]}
              <small className="mt-0.5 block text-[15px] text-text-2">{t(`languages.${language}`)}</small>
            </span>
            {language === current && (
              <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[18px] fill-none stroke-brand stroke-[2.4]">
                <path d="M5 12.5l4.5 4.5L19 7.5" />
              </svg>
            )}
          </button>
        ))}
      </div>
      <span className="px-4 text-[13px] text-text-3">{t('account.languageNote')}</span>
    </div>
  );
}
