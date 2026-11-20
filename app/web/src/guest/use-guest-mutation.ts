import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { ApiError, newIdempotencyKey } from '../api/errors';
import type { GuestTask } from './api/types';
import { useToast } from './components/Toast';
import { guestQueryKey } from './queries';
import { currentSessionToken, useReadySession } from './session';

/** 一張需求上的寫入；`refused` 是後端回 `409` 時要浮出的字，`null` 代表不提示。 */
interface GuestMutation<A> {
  send: (sessionToken: string, args: A, idempotencyKey: string) => Promise<GuestTask>;
  succeeded: string;
  refused: string | null;
  notSent: string;
}

/**
 * 進度頁上取消與評分共用的寫入：送出中鎖住；成功時拿後端回的這張蓋掉清單裡的舊資料、提示、再重讀；
 * 被拒（`409`）時重讀到真正的狀態；連不上或 `5xx` 只提示，不動畫面上的資料。
 */
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
      queryClient.setQueryData<GuestTask[]>(requestsKey, (list) =>
        list?.map((each) => (each.id === updated.id ? updated : each)),
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
