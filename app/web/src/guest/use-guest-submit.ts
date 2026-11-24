import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { goToSent, submitSequentially, type SubmitResult } from './lib/submit';
import { guestQueryKey } from './queries';
import { currentSessionToken, useReadySession } from './session';

export function useGuestSubmit() {
  const session = useReadySession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [sending, setSending] = useState(false);
  // 同一個 tick 連按兩下時 state 還沒更新，靠 ref 擋第二下。
  const busy = useRef(false);

  const submit = async <T>(
    items: readonly T[],
    send: (item: T, sessionToken: string, idempotencyKey: string) => Promise<{ id: string }>,
  ): Promise<SubmitResult | null> => {
    if (busy.current) return null;
    busy.current = true;
    setSending(true);
    const result = await submitSequentially(items, (item, key) => send(item, currentSessionToken(), key));
    busy.current = false;
    setSending(false);
    const refetch = result.created.length > 0 ? queryClient.invalidateQueries({ queryKey: guestQueryKey(session.qrToken, 'requests') }) : null;
    if (result.kind === 'all-sent') {
      await refetch;
      goToSent(navigate, session.qrToken, result.created);
    }
    return result;
  };

  return { sending, submit };
}
