import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LanguageMenu } from '../../features/account';
import { NEW_PASSWORD_MAX_LENGTH, NEW_PASSWORD_RULES } from '../../lib/new-password-rules';
import { useChangePasswordForm } from './use-change-password-form';

/**
 * 改密碼畫面（路徑 `/{飯店識別代碼}/change-password`）。拿臨時密碼登入的人先在這裡設定自己的密碼，
 * 才進得了其他櫃檯畫面。版面沿用登入頁的置中版型；不需要再輸入一次臨時密碼，所以只有兩欄。
 */
export function ChangePasswordPage() {
  const { t } = useTranslation();
  const form = useChangePasswordForm();

  return (
    <main data-page="change-password" className="flex min-h-screen min-w-shell flex-col bg-bg font-sans text-text">
      <LanguageMenu />
      <div className="flex flex-auto justify-center px-6 pt-6 pb-12">
        <form className="my-auto flex w-full max-w-auth-form flex-col gap-[22px]" noValidate onSubmit={form.submit}>
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
              <h1 className="m-0 text-[28px] leading-[1.2] font-bold">{t('changePassword.title')}</h1>
              <div className="mt-1.5 text-[15px] leading-[1.45] text-text-2">{t('changePassword.note')}</div>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <div className="overflow-hidden rounded-card bg-surface">
              <PasswordRow
                id="new-password"
                label={t('changePassword.newPassword')}
                value={form.newPassword}
                onChange={form.changeNewPassword}
              />
              <PasswordRow
                id="confirm-password"
                label={t('changePassword.confirmPassword')}
                value={form.confirmPassword}
                onChange={form.changeConfirmPassword}
                divided
              />
            </div>

            {form.error !== null && (
              <p role="alert" className="m-0 px-4 text-[13px] leading-[1.4] text-danger">
                {t(form.error)}
              </p>
            )}

            {/* 規則只是即時告訴同仁哪一條還沒符合，不能點；螢幕報讀軟體讀到的是「規則文字，已勾選／未勾選」。 */}
            <div className="flex flex-col gap-1 px-4 pt-0.5">
              {NEW_PASSWORD_RULES.map((rule) => {
                const passed = form.rules[rule];
                return (
                  <div
                    key={rule}
                    role="checkbox"
                    aria-checked={passed}
                    aria-readonly="true"
                    className={`flex items-center gap-2 text-[13px] ${passed ? 'text-text' : 'text-[#6D6D72]'}`}
                  >
                    <span
                      aria-hidden="true"
                      className={`size-2 shrink-0 rounded-full ${passed ? 'bg-[#34C759]' : 'bg-[#C7C7CC]'}`}
                    />
                    {t(`changePassword.rules.${rule}`)}
                  </div>
                );
              })}
            </div>
          </div>

          <div className="flex flex-col gap-1">
            <button
              type="submit"
              disabled={!form.canSubmit}
              className="min-h-[54px] w-full rounded-full border-none bg-brand text-[17px] font-semibold text-white disabled:bg-line"
            >
              {t('changePassword.submit')}
            </button>
            <button
              type="button"
              onClick={form.logout}
              className="min-h-touch self-center border-none bg-transparent px-4 text-[15px] text-brand"
            >
              {t('changePassword.logout')}
            </button>
          </div>
        </form>
      </div>
    </main>
  );
}

/** 白卡裡的一列：左側欄名、中間輸入欄、右側顯示／隱藏鈕，和登入頁的密碼列同一個樣子。 */
function PasswordRow({
  id,
  label,
  value,
  onChange,
  divided = false,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (input: string) => void;
  divided?: boolean;
}) {
  const { t } = useTranslation();
  const [shown, setShown] = useState(false);

  return (
    <div
      className={`flex min-h-[52px] items-center gap-2 pr-1.5 pl-4 ${divided ? 'border-t-[0.5px] border-line-soft' : ''}`}
    >
      <label htmlFor={id} className="w-form-label shrink-0 text-[17px] leading-[1.25]">
        {label}
      </label>
      <input
        id={id}
        type={shown ? 'text' : 'password'}
        autoComplete="new-password"
        maxLength={NEW_PASSWORD_MAX_LENGTH}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="min-h-touch min-w-0 flex-auto border-none bg-transparent p-0 text-[17px] outline-none"
      />
      <button
        type="button"
        aria-label={t(shown ? 'login.hidePassword' : 'login.showPassword')}
        onClick={() => setShown((current) => !current)}
        className="flex size-touch shrink-0 items-center justify-center border-none bg-transparent p-0 text-text-4"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[22px] fill-none stroke-current stroke-[1.8]">
          {!shown && <path d="M4 4l16 16" />}
          <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
          {shown && <circle cx="12" cy="12" r="3" />}
        </svg>
      </button>
    </div>
  );
}
