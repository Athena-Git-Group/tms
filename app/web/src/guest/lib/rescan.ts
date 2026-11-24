import type { SessionRenewal } from '../api/client';
import type { ScanResponse } from '../api/types';

export interface RenewalDeps {
  currentSession: () => string | null;
  rescan: () => Promise<ScanResponse>;
  renewed: (response: ScanResponse) => void;
  failed: (error: unknown) => void;
}

export function createRenewal(deps: RenewalDeps): SessionRenewal {
  let inFlight: Promise<string> | null = null;
  const renew = (rejected: string): Promise<string> => {
    if (inFlight) return inFlight;
    const current = deps.currentSession();
    if (current !== null && current !== rejected) return Promise.resolve(current);
    inFlight = deps
      .rescan()
      .then(
        (response) => {
          deps.renewed(response);
          return response.sessionToken;
        },
        (error: unknown) => {
          deps.failed(error);
          throw error;
        },
      )
      .finally(() => {
        inFlight = null;
      });
    return inFlight;
  };
  return { renew, renewedSessionRejected: deps.failed };
}
