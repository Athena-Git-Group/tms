import { useMutation } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { loginToFrontdesk } from '../../api/auth';
import { ApiError } from '../../api/client';
import { queryClient } from '../../api/query-client';
import { logOut } from '../../lib/log-out';
import { canSubmitLogin, normalizeLoginId } from '../../lib/login-form';
import { useLoginNoticeStore, type PendingLoginNotice } from '../../stores/login-notice';
import { useSessionStore } from '../../stores/session';

/**
 * 登入失敗後白卡下方的提示。帳密錯誤與連不上是一行紅字；被鎖定與已停用是有標題與內文的提示卡，
 * 兩種樣子一眼分得出來，同仁才不會把「被鎖住」誤會成「打錯字」而一直重試。
 */
export type Notice =
  | { kind: 'line'; text: 'login.credentialsInvalid' | 'login.networkError' }
  | { kind: 'card'; title: 'login.lockedTitle'; body: 'login.lockedBody' }
  | { kind: 'card'; title: 'login.disabledTitle'; body: 'login.disabledBody' }
  | { kind: 'card'; title: 'login.deskBlockedTitle'; body: 'login.deskBlockedBody' };

const PENDING_NOTICES: Record<PendingLoginNotice, Notice> = {
  deskBlocked: { kind: 'card', title: 'login.deskBlockedTitle', body: 'login.deskBlockedBody' },
};

/**
 * 後端說的失敗種類換成提示。三種失敗以外的回應（例如後端或代理出錯）沒有可以告訴同仁的原因，
 * 和連不上一樣請他稍後再試。
 */
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

/**
 * 登入表單的兩欄、提示與送出。員工編號照打的樣子收、只截到上限；改任何一欄都清掉提示；
 * 登入失敗後清空密碼，只有連不上時兩欄都留著。登入成功只更新登入狀態。
 *
 * 登入的是網址上那一家飯店。這個分頁原本已經登入別家飯店時（路由那一層把他帶來這裡），
 * 登入成功就換成這一家：先照登出的做法清掉上一位留下的東西，再記新的登入狀態，所以原本那一家要重新登入。
 */
export function useLoginForm(hotelCode: string) {
  const setSession = useSessionStore((state) => state.setSession);
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const pendingNotice = useLoginNoticeStore((state) => state.pending);
  const clearPendingNotice = useLoginNoticeStore((state) => state.clear);
  const [notice, setNotice] = useState<Notice | null>(() =>
    pendingNotice === null ? null : PENDING_NOTICES[pendingNotice],
  );

  // 提示已經接到這一頁的狀態裡，留在記憶體的那一筆要清掉，下次回到登入頁才不會又跳出來。
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
      // 連不上時後端沒有判斷過這組帳密，兩欄都留著讓同仁直接再按一次。
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
