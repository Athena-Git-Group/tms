import { useQuery } from '@tanstack/react-query';
import { createContext, useContext } from 'react';
import { listRequests, readHome } from './api/client';
import type { GuestQuota, GuestRequestList } from './api/types';
import type { GuestData } from './lib/live-rereads';
import { quotaRefreshMs } from './lib/quota';
import { useGuestSessionStore } from './session';

export const GuestRereadContext = createContext<number | false>(false);

export function guestQueryKey(qrToken: string, data: GuestData) {
  return ['guest', qrToken, data] as const;
}

export function useGuestHome(qrToken: string) {
  const session = useGuestSessionStore((state) => (state.session?.qrToken === qrToken ? state.session : null));
  return useQuery({
    queryKey: guestQueryKey(qrToken, 'home'),
    queryFn: ({ signal }) => readHome(session!.sessionToken, signal),
    enabled: session !== null,
    initialData: session ? { infoPages: session.infoPages, menu: session.menu } : undefined,
  });
}

function useRequestListQuery<T>(
  qrToken: string,
  select: (list: GuestRequestList) => T,
  refetchInterval: number | false | ((query: { state: { data?: GuestRequestList } }) => number | false),
) {
  const sessionToken = useGuestSessionStore((state) =>
    state.session?.qrToken === qrToken ? state.session.sessionToken : null,
  );
  return useQuery({
    queryKey: guestQueryKey(qrToken, 'requests'),
    queryFn: ({ signal }) => listRequests(sessionToken!, signal),
    enabled: sessionToken !== null,
    select,
    refetchInterval,
  });
}

const selectRequests = (list: GuestRequestList) => list.requests;
const selectQuota = (list: GuestRequestList) => list.quota;
const refreshWhenSlotFrees = (query: { state: { data?: GuestRequestList } }) => quotaRefreshMs(query.state.data?.quota ?? null);

export function useGuestRequests(qrToken: string) {
  return useRequestListQuery(qrToken, selectRequests, useContext(GuestRereadContext));
}

export function useGuestQuota(qrToken: string): GuestQuota | null {
  return useRequestListQuery(qrToken, selectQuota, refreshWhenSlotFrees).data ?? null;
}
