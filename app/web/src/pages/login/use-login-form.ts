import { useMutation } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { loginToFrontdesk } from '../../api/auth';
import { ApiError } from '../../api/client';
import { queryClient } from '../../api/query-client';
import { logOut } from '../../lib/log-out';
import { canSubmitLogin, normalizeLoginId } from '../../lib/login-form';
import { useLoginNoticeStore, type PendingLoginNotice } from '../../stores/login-notice';
import { useSessionStore } from '../../stores/session';

export type Notice =
  | { kind: 'line'; text: 'login.credentialsInvalid' | 'login.networkError' }
  | { kind: 'card'; title: 'login.lockedTitle'; body: 'login.lockedBody' }
  | { kind: 'card'; title: 'login.disabledTitle'; body: 'login.disabledBody' }
  | { kind: 'card'; title: 'login.deskBlockedTitle'; body: 'login.deskBlockedBody' };

const PENDING_NOTICES: Record<PendingLoginNotice, Notice> = {
  deskBlocked: { kind: 'card', title: 'login.deskBlockedTitle', body: 'login.deskBlockedBody' },
};

function noticeFor(error: unknown): Notice {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'credentials_invalid':
        return { kind: 'line', text: 'login.credentialsInvalid' };
      case 'account_locked':
        return { kind: 'card', title: 'login.lockedTitle', body: 'login.lockedBody' };
      case 'account_disabled':
        return { kind: 'card', title: 'login.disabledTitle', body: 'login.disabledBody' };
    }
  }
  return { kind: 'line', text: 'login.networkError' };
}

export function useLoginForm(hotelCode: string) {
  const setSession = useSessionStore((state) => state.setSession);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const pendingNotice = useLoginNoticeStore((state) => state.pending);
  const clearPendingNotice = useLoginNoticeStore((state) => state.clear);
  const [notice, setNotice] = useState<Notice | null>(() =>
    pendingNotice === null ? null : PENDING_NOTICES[pendingNotice],
  );

  useEffect(() => {
    if (pendingNotice !== null) clearPendingNotice();
  }, [pendingNotice, clearPendingNotice]);

  const login = useMutation({
    mutationFn: (credentials: { loginId: string; password: string }) =>
      loginToFrontdesk(credentials.loginId, credentials.password, hotelCode),
    onSuccess: (response) => {
      if (useSessionStore.getState().session !== null) logOut(queryClient);
      setSession(response, hotelCode);
    },
    onError: (error) => {
      const next = noticeFor(error);
      setNotice(next);
      if (!(next.kind === 'line' && next.text === 'login.networkError')) setPassword('');
    },
  });

  const canSubmit = canSubmitLogin({ loginId, password, submitting: login.isPending });

  function changeLoginId(input: string) {
    setLoginId(normalizeLoginId(input));
    setNotice(null);
  }

  function changePassword(input: string) {
    setPassword(input);
    setNotice(null);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setNotice(null);
    login.mutate({ loginId, password });
  }

  return { loginId, password, notice, canSubmit, changeLoginId, changePassword, submit };
}
