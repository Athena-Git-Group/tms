import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { readPublicHotel } from '../../api/auth';
import { AlertDialog } from '../../components/AlertDialog';
import { LanguageMenu } from '../../features/account';
import { LOGIN_ID_MAX_LENGTH, PASSWORD_MAX_LENGTH } from '../../lib/login-form';
import { useHotelCode } from '../../lib/use-hotel-code';
import { useLoginForm } from './use-login-form';

/**
 * 登入頁（路徑 `/{飯店識別代碼}/login`）。飯店名稱用網址上的代碼查，登入也只登入這一家。
 * 登入成功只更新登入狀態，換去哪一頁由路由那一層依登入狀態決定。
 */
export function LoginPage() {
  const { t } = useTranslation();
  const hotelCode = useHotelCode();
  const { loginId, password, notice, canSubmit, changeLoginId, changePassword, submit } = useLoginForm(hotelCode);
  const [passwordShown, setPasswordShown] = useState(false);
  const [forgotOpen, setForgotOpen] = useState(false);

  // 查不到、還沒回來或查詢失敗時都不畫飯店名稱那一行，登入照常可用，所以失敗不重試。
  const hotel = useQuery({
    queryKey: ['public-hotel', hotelCode],
    queryFn: () => readPublicHotel(hotelCode),
    retry: false,
    staleTime: Infinity,
  });

  return (
    <main data-page="login" className="flex min-h-screen min-w-shell flex-col bg-bg font-sans text-text">
      <LanguageMenu />
      <div className="flex flex-auto justify-center px-6 pt-6 pb-12">
        <form className="my-auto flex w-full max-w-auth-form flex-col gap-[22px]" noValidate onSubmit={submit}>
          <div className="flex flex-col items-center gap-3.5 text-center">
            <span
              aria-hidden="true"
              className="flex size-[72px] items-center justify-center rounded-[18px] bg-brand text-white shadow-[0_6px_16px_rgba(12,118,128,.25)]"
            >
              <svg viewBox="0 0 24 24" className="size-10 fill-none stroke-current stroke-[1.8]">
                <rect x="3" y="5" width="18" height="14" rx="2.5" />
                <circle cx="12" cy="10.5" r="2.2" />
                <path d="M8 16.5a4 4 0 0 1 8 0" />
              </svg>
            </span>
            <div>
              <h1 className="m-0 text-[28px] leading-[1.2] font-bold">{t('login.title')}</h1>
              {hotel.data && (
                <div className="mt-1.5 text-[15px] font-semibold text-text-2">{hotel.data.name}</div>
              )}
              <div className="mt-1.5 text-[15px] leading-[1.45] text-text-2">{t('login.sharedDeviceNote')}</div>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <div className="overflow-hidden rounded-card bg-surface">
              <div className="flex min-h-[52px] items-center gap-2 pr-1.5 pl-4">
                <label htmlFor="login-id" className="w-form-label shrink-0 text-[17px] leading-[1.25]">
                  {t('login.loginId')}
                </label>
                <input
                  id="login-id"
                  type="text"
                  autoComplete="off"
                  autoCapitalize="none"
                  spellCheck={false}
                  maxLength={LOGIN_ID_MAX_LENGTH}
                  value={loginId}
                  onChange={(event) => changeLoginId(event.target.value)}
                  className="min-h-touch min-w-0 flex-auto border-none bg-transparent p-0 text-[17px] outline-none"
                />
              </div>
              <div className="flex min-h-[52px] items-center gap-2 border-t-[0.5px] border-line-soft pr-1.5 pl-4">
                <label htmlFor="login-password" className="w-form-label shrink-0 text-[17px] leading-[1.25]">
                  {t('login.password')}
                </label>
                <input
                  id="login-password"
                  type={passwordShown ? 'text' : 'password'}
                  autoComplete="off"
                  maxLength={PASSWORD_MAX_LENGTH}
                  value={password}
                  onChange={(event) => changePassword(event.target.value)}
                  className="min-h-touch min-w-0 flex-auto border-none bg-transparent p-0 text-[17px] outline-none"
                />
                <button
                  type="button"
                  aria-label={t(passwordShown ? 'login.hidePassword' : 'login.showPassword')}
                  onClick={() => setPasswordShown((shown) => !shown)}
                  className="flex size-touch shrink-0 items-center justify-center border-none bg-transparent p-0 text-text-4"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[22px] fill-none stroke-current stroke-[1.8]">
                    {!passwordShown && <path d="M4 4l16 16" />}
                    <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
                    {passwordShown && <circle cx="12" cy="12" r="3" />}
                  </svg>
                </button>
              </div>
            </div>

            {notice?.kind === 'line' && (
              <p role="alert" className="m-0 px-4 text-[13px] leading-[1.4] text-danger">
                {t(notice.text)}
              </p>
            )}
            {notice?.kind === 'card' && (
              <div role="alert" className="flex items-start gap-3 rounded-2xl bg-err-bg px-4 py-3">
                <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[26px] shrink-0 fill-none stroke-danger stroke-[1.8]">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 7.5v5.5M12 16v.5" />
                </svg>
                <div>
                  <b className="block text-[17px] leading-[1.3] font-semibold">{t(notice.title)}</b>
                  <span className="mt-0.5 block text-[15px] leading-[1.4] text-text-2">{t(notice.body)}</span>
                </div>
              </div>
            )}
          </div>

          <div className="flex flex-col gap-1">
            <button
              type="submit"
              disabled={!canSubmit}
              className="min-h-[54px] w-full rounded-full border-none bg-brand text-[17px] font-semibold text-white disabled:bg-line"
            >
              {t('login.submit')}
            </button>
            <button
              type="button"
              onClick={() => setForgotOpen(true)}
              className="min-h-touch self-center border-none bg-transparent px-4 text-[15px] text-brand"
            >
              {t('login.forgotPassword')}
            </button>
          </div>
        </form>
      </div>

      {forgotOpen && (
        <AlertDialog
          title={t('login.forgotTitle')}
          body={t('login.forgotBody')}
          onClose={() => setForgotOpen(false)}
        />
      )}
    </main>
  );
}
