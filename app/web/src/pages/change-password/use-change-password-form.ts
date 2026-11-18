import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { changeOwnPassword } from '../../api/auth';
import { ApiError } from '../../api/client';
import { queryClient } from '../../api/query-client';
import { allNewPasswordRulesPassed, checkNewPassword } from '../../lib/new-password-rules';
import { logOut } from '../../lib/log-out';
import { useHotelCode } from '../../lib/use-hotel-code';
import { canUseDesk } from '../../lib/route-access';
import { useLoginNoticeStore } from '../../stores/login-notice';
import { useSessionStore } from '../../stores/session';

/** 白卡下方的一行紅字。和臨時密碼相同是後端才分辨得出的，其餘沒有成功的情形都請同仁檢查連線再試。 */
export type ChangePasswordError = 'changePassword.sameAsTemporary' | 'changePassword.networkError';

function errorFor(error: unknown): ChangePasswordError {
  if (error instanceof ApiError && error.code === 'new_password_invalid') return 'changePassword.sameAsTemporary';
  return 'changePassword.networkError';
}

/**
 * 改密碼畫面的兩欄、四條規則、錯誤與送出。換去哪一頁由路由那一層依登入狀態決定。
 * 能用櫃檯畫面的人拿後端換發的憑證當新的登入狀態，不必再登入一次。
 * 不能用櫃檯畫面的人沒有可以進的頁面，所以和登出一樣清掉屬於他的東西，再替登入頁留一筆提示，讓他知道新密碼已經生效。
 */
export function useChangePasswordForm() {
  const setSession = useSessionStore((state) => state.setSession);
  // 路由那一層只在網址上的飯店和登入的那家相同時才讓人進這一頁，所以網址上的代碼就是登入的那一家。
  const hotelCode = useHotelCode();
  const leaveLoginNotice = useLoginNoticeStore((state) => state.leave);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<ChangePasswordError | null>(null);

  const change = useMutation({
    mutationFn: changeOwnPassword,
    onSuccess: (session) => {
      if (canUseDesk(session.role)) {
        setSession(session, hotelCode);
        return;
      }
      // 提示要在清除之後才留，清除那一步會連留給登入頁的提示一起清掉。
      logOut(queryClient);
      leaveLoginNotice('deskBlocked');
    },
    onError: (failure) => setError(errorFor(failure)),
  });

  const rules = checkNewPassword(newPassword, confirmPassword);
  const canSubmit = allNewPasswordRulesPassed(rules) && !change.isPending;

  function changeNewPassword(input: string) {
    setNewPassword(input);
    setError(null);
  }

  function changeConfirmPassword(input: string) {
    setConfirmPassword(input);
    setError(null);
  }

  function logout() {
    logOut(queryClient);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setError(null);
    change.mutate(newPassword);
  }

  return {
    newPassword,
    confirmPassword,
    rules,
    error,
    canSubmit,
    changeNewPassword,
    changeConfirmPassword,
    submit,
    logout,
  };
}
