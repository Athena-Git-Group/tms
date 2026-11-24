import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { authHeaders, backendUrl } from '../../api/client';
import { useSessionStore } from '../../stores/session';
import { INITIAL_LIVE_STATE, type LiveState } from './live-connection';
import { followBoard } from './follow-board';
import { openStream } from './stream-client';

export function useLiveUpdates(): LiveState {
  const queryClient = useQueryClient();
  const token = useSessionStore((state) => state.session?.token);
  const [live, setLive] = useState<{ token: string | undefined; state: LiveState }>({ token, state: INITIAL_LIVE_STATE });

  useEffect(() => {
    if (!token) return undefined;
    const follower = followBoard(queryClient);
    const close = openStream({
      url: backendUrl('/staff/stream'),
      headers: authHeaders(),
      onEvent: follower.onStreamEvent,
      onState: (state) =>
        setLive((previous) => (previous.token === token && previous.state === state ? previous : { token, state })),
    });
    return () => {
      close();
      follower.stop();
    };
  }, [token, queryClient]);

  return live.token === token ? live.state : INITIAL_LIVE_STATE;
}
