import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { backendUrl } from '../api/errors';
import { INITIAL_LIVE_STATE } from '../features/realtime/live-connection';
import { openStream, type StreamOptions } from '../features/realtime/stream-client';
import { GUEST_SESSION_HEADER } from './api/client';
import { guestRereadMs, guestTransition } from './lib/live';
import { rereadsOn } from './lib/live-rereads';
import { guestQueryKey } from './queries';
import { useGuestSessionStore } from './session';

/** 住客這次住宿的需求變化訊號；收到之後要做什麼由呼叫的一方決定。 */
export function openGuestStream(
  sessionToken: string,
  handlers: Pick<StreamOptions, 'onEvent' | 'onState'>,
): () => void {
  return openStream({
    url: backendUrl('/guest/stream'),
    headers: { [GUEST_SESSION_HEADER]: sessionToken },
    transition: guestTransition,
    ...handlers,
  });
}

/**
 * 換到 session 之後一直開著的串流，掛在 `/g/{代碼}` 這一層，換頁不重開；session 換了才重開。
 * 回傳需求清單該不該定時重讀，畫面上不顯示任何連線狀態。
 */
export function useGuestLiveUpdates(qrToken: string, ready: boolean): number | false {
  const queryClient = useQueryClient();
  const sessionToken = useGuestSessionStore((state) =>
    state.session?.qrToken === qrToken ? state.session.sessionToken : null,
  );
  const [live, setLive] = useState(INITIAL_LIVE_STATE);

  useEffect(() => {
    if (!ready || sessionToken === null) return undefined;
    return openGuestStream(sessionToken, {
      onEvent: ({ event }) => {
        for (const data of rereadsOn(event)) void queryClient.invalidateQueries({ queryKey: guestQueryKey(qrToken, data) });
      },
      onState: setLive,
    });
  }, [qrToken, ready, sessionToken, queryClient]);

  return guestRereadMs(live);
}
