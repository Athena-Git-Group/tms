import { useQuery } from '@tanstack/react-query';
import { createContext, useContext } from 'react';
import { listRequests, readHome } from './api/client';
import type { GuestData } from './lib/live-rereads';
import { useGuestSessionStore } from './session';

/** 需求清單定時重讀的間隔；連線正常時是 `false`。由進入層依串流的狀態提供。 */
export const GuestRereadContext = createContext<number | false>(false);

export function guestQueryKey(qrToken: string, data: GuestData) {
  return ['guest', qrToken, data] as const;
}

/** 換 session 的回應已經帶了首頁資料，先拿它當初值，首頁不用等第二次讀取才畫得出來。 */
export function useGuestHome(qrToken: string) {
  const session = useGuestSessionStore((state) => (state.session?.qrToken === qrToken ? state.session : null));
  return useQuery({
    queryKey: guestQueryKey(qrToken, 'home'),
    queryFn: ({ signal }) => readHome(session!.sessionToken, signal),
    enabled: session !== null,
    initialData: session ? { infoPages: session.infoPages, menu: session.menu } : undefined,
  });
}

export function useGuestRequests(qrToken: string) {
  const sessionToken = useGuestSessionStore((state) =>
    state.session?.qrToken === qrToken ? state.session.sessionToken : null,
  );
  return useQuery({
    queryKey: guestQueryKey(qrToken, 'requests'),
    queryFn: ({ signal }) => listRequests(sessionToken!, signal),
    enabled: sessionToken !== null,
    refetchInterval: useContext(GuestRereadContext),
  });
}
