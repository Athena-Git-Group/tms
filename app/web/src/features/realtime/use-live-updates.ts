import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useSessionStore } from '../../stores/session';
import { INITIAL_LIVE_STATE, type LiveState } from './live-connection';
import { followBoard } from './follow-board';
import { openStaffStream } from './stream-client';

/**
 * 讓看板跟上現場：連著員工事件串流，收到的訊號交給 `followBoard` 更新看板快取。掛在外框上，總覽看板與待派單頁共用同一條連線。
 *
 * 換了登入憑證（登出、換人登入）就關掉舊連線；沒有登入時不連。
 *
 * 回傳即時連線的狀態（見 `live-connection.ts`），外框從它讀要不要顯示提醒列、看板要不要定時重抓。
 * 狀態跟著登入憑證記，換了憑證就從「剛開頁」重新算起。
 */
export function useLiveUpdates(): LiveState {
  const queryClient = useQueryClient();
  const token = useSessionStore((state) => state.session?.token);
  const [live, setLive] = useState<{ token: string | undefined; state: LiveState }>({ token, state: INITIAL_LIVE_STATE });

  useEffect(() => {
    if (!token) return undefined;
    const follower = followBoard(queryClient);
    const close = openStaffStream({
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
