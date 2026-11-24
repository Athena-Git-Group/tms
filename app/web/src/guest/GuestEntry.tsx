import { useEffect, useReducer, type ReactNode } from 'react';
import { scan, setSessionRenewal } from './api/client';
import type { ScanResponse } from './api/types';
import { PageLoading } from './components/PageLoading';
import { RetryNotice } from './components/RetryNotice';
import { UnavailableNotice } from './components/UnavailableNotice';
import { GuestShell } from './GuestShell';
import { useDocumentTitle } from './use-document-title';
import { ENTRY_LOADING, entryReducer, scanErrorEvent, type EntryState } from './lib/entry';
import { createRenewal } from './lib/rescan';
import { GuestRereadContext } from './queries';
import { useGuestLiveUpdates } from './realtime';
import { clearGuestSession, readGuestSession, useGuestSessionStore } from './session';

/**
 * 同一枚代碼同時只換一次 session。開發模式下元件會掛兩次，不合併的話後端每次都多開一個住客 session。
 */
const scansInFlight = new Map<string, Promise<ScanResponse>>();

function scanOnce(qrToken: string): Promise<ScanResponse> {
  const pending = scansInFlight.get(qrToken);
  if (pending) return pending;
  const started = scan(qrToken).finally(() => scansInFlight.delete(qrToken));
  scansInFlight.set(qrToken, started);
  return started;
}

function initialState(qrToken: string): EntryState {
  return useGuestSessionStore.getState().session?.qrToken === qrToken ? { kind: 'ready' } : ENTRY_LOADING;
}

export function GuestEntry({ qrToken, children }: { qrToken: string; children: ReactNode }) {
  const setSession = useGuestSessionStore((state) => state.setSession);
  const [state, dispatch] = useReducer(entryReducer, qrToken, initialState);

  useEffect(() => {
    if (state.kind !== 'loading' && state.kind !== 'retrying') return undefined;
    const stored = state.kind === 'loading' ? readGuestSession(qrToken) : null;
    if (stored) {
      setSession(qrToken, stored);
      dispatch({ type: 'scan-succeeded' });
      return undefined;
    }
    let current = true;
    scanOnce(qrToken).then(
      (response) => {
        if (!current) return;
        setSession(qrToken, response);
        dispatch({ type: 'scan-succeeded' });
      },
      (error: unknown) => {
        if (current) dispatch(scanErrorEvent(error));
      },
    );
    return () => {
      current = false;
    };
  }, [qrToken, state.kind, setSession]);

  const ready = state.kind === 'ready';
  const rereadMs = useGuestLiveUpdates(qrToken, ready);
  useEffect(() => {
    if (!ready) return undefined;
    setSessionRenewal(
      createRenewal({
        currentSession: () => {
          const session = useGuestSessionStore.getState().session;
          return session?.qrToken === qrToken ? session.sessionToken : null;
        },
        rescan: () => {
          clearGuestSession(qrToken);
          return scanOnce(qrToken);
        },
        renewed: (response) => setSession(qrToken, response),
        failed: (error) => dispatch(scanErrorEvent(error)),
      }),
    );
    return () => setSessionRenewal(null);
  }, [qrToken, ready, setSession]);

  const retry = () => dispatch({ type: 'retry-pressed' });
  return (
    <GuestRereadContext.Provider value={rereadMs}>
      <GuestShell>{entryView(state, children, retry)}</GuestShell>
    </GuestRereadContext.Provider>
  );
}

function entryView(state: EntryState, children: ReactNode, retry: () => void): ReactNode {
  switch (state.kind) {
    case 'loading':
      return <EntryLoading />;
    case 'ready':
      return children;
    case 'unavailable':
      return <UnavailableNotice />;
    case 'retry':
    case 'retrying':
      return <RetryNotice retrying={state.kind === 'retrying'} onRetry={retry} />;
  }
}

function EntryLoading() {
  useDocumentTitle('');
  return <PageLoading />;
}
