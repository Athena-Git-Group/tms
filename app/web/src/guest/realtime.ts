import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { backendUrl } from '../api/errors';
import { GUEST_LIVE_POLICY, INITIAL_LIVE_STATE, transitionFor } from '../features/realtime/live-connection';
import { openStream, type StreamOptions } from '../features/realtime/stream-client';
import { GUEST_SESSION_HEADER } from './api/client';
import { guestRereadMs } from './lib/live';
import { rereadsOn } from './lib/live-rereads';
import { guestQueryKey } from './queries';
import { useGuestSessionStore } from './session';

export function openGuestStream(
  sessionToken: string,
  handlers: Pick<StreamOptions, 'onEvent' | 'onState'>,
): () => void {
  return openStream({
    url: backendUrl('/guest/stream'),
    headers: { [GUEST_SESSION_HEADER]: sessionToken },
    transition: transitionFor(GUEST_LIVE_POLICY),
    ...handlers,
  });
}

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
