import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { goToSent, submitSequentially, type SubmitResult } from './lib/submit';
import { guestQueryKey } from './queries';
import { recordSent } from './sent-order';
import { currentSessionToken, useReadySession } from './session';

/**
 * 備品補充與設備報修共用的送出：按下到回覆之間鎖住，依序送出、記下這支手機送出的先後、
 * 有建立就重讀清單，全部建立成功就換到送出成功頁。停下來時把結果交回去，由該頁決定畫面。
 */
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
    // 每一張送出前才取 session，中途被靜靜重換過時用新的那一把。
    const result = await submitSequentially(items, (item, key) => send(item, currentSessionToken(), key));
    recordSent(session.qrToken, result.created);
    busy.current = false;
    setSending(false);
    const refetch = result.created.length > 0 ? queryClient.invalidateQueries({ queryKey: guestQueryKey(session.qrToken, 'requests') }) : null;
    if (result.kind === 'all-sent') {
      await refetch;
      goToSent(navigate, session.qrToken, result.created);
    }
    // 停下來時不等重讀：可能正是連不上，重讀會一直重試，提示框不能等它。
    return result;
  };

  return { sending, submit };
}
