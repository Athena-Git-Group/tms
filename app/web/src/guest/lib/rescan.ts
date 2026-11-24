import type { ScanResponse } from '../api/types';

export interface RenewalDeps {
  /** 這個分頁目前手上的 session；還沒有時是 `null`。 */
  currentSession: () => string | null;
  /** 丟掉手上的 session，用網址上的代碼換一把新的。 */
  rescan: () => Promise<ScanResponse>;
  renewed: (response: ScanResponse) => void;
  /** 換不到新的 session（空房、連不上），或剛換到的又被拒絕。 */
  failed: (error: unknown) => void;
}

/**
 * 呼叫被回 401 時換 session 的那一步，給 `setUnauthorizedHandler`。同時被拒的幾個呼叫共用同一次重換；
 * 已經有人換過了（手上的不是被拒的那一把）就直接拿手上的。剛換到的那一把又被拒時不再重換，
 * 免得 401 與重換之間無限打轉。
 */
export function createRenewal(deps: RenewalDeps): (rejected: string) => Promise<string> {
  let inFlight: Promise<string> | null = null;
  let issued: string | null = null;
  return (rejected) => {
    if (inFlight) return inFlight;
    const current = deps.currentSession();
    if (current !== null && current !== rejected) return Promise.resolve(current);
    if (rejected === issued) {
      const error = new Error('剛換到的 session 又被後端拒絕');
      deps.failed(error);
      return Promise.reject(error);
    }
    inFlight = deps
      .rescan()
      .then(
        (response) => {
          issued = response.sessionToken;
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
}
