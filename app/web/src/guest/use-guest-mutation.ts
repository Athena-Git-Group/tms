import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { ApiError, newIdempotencyKey } from '../api/errors';
import type { GuestRequestList, GuestTask } from './api/types';
import { useToast } from './components/Toast';
import { guestQueryKey } from './queries';
import { currentSessionToken, useReadySession } from './session';

interface GuestMutation<A> {
  send: (sessionToken: string, args: A, idempotencyKey: string) => Promise<GuestTask>;
  succeeded: string;
  refused: string | null;
  notSent: string;
}

export function useGuestMutation<A>({ send, succeeded, refused, notSent }: GuestMutation<A>) {
  const { qrToken } = useReadySession();
  const queryClient = useQueryClient();
  const show = useToast();
  const [busy, setBusy] = useState(false);
  // 同一個 tick 連按兩下時 state 還沒更新，靠 ref 擋第二下。
  const locked = useRef(false);
  const requestsKey = guestQueryKey(qrToken, 'requests');

  const run = async (args: A): Promise<void> => {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    try {
      const updated = await send(currentSessionToken(), args, newIdempotencyKey());
      queryClient.setQueryData<GuestRequestList>(requestsKey, (list) =>
        list && { ...list, requests: list.requests.map((each) => (each.id === updated.id ? updated : each)) },
      );
      show(succeeded, 'ok');
      void queryClient.invalidateQueries({ queryKey: requestsKey });
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        if (refused !== null) show(refused, 'warn');
        await queryClient.invalidateQueries({ queryKey: requestsKey });
      } else {
        show(notSent, 'warn');
      }
    } finally {
      locked.current = false;
      setBusy(false);
    }
  };

  return { busy, run };
}
