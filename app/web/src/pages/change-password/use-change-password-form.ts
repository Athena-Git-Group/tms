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

export type ChangePasswordError = 'changePassword.sameAsTemporary' | 'changePassword.networkError';

function errorFor(error: unknown): ChangePasswordError {
  if (error instanceof ApiError && error.code === 'new_password_invalid') return 'changePassword.sameAsTemporary';
  return 'changePassword.networkError';
}

export function useChangePasswordForm() {
  const setSession = useSessionStore((state) => state.setSession);
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
