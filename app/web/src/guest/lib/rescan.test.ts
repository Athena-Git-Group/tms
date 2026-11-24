import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/errors';
import type { ScanResponse } from '../api/types';
import { createRenewal } from './rescan';

function scanned(sessionToken: string): ScanResponse {
  return { sessionToken, expiresAt: '2026-10-27T03:00:00Z', roomNo: '311', infoPages: [], menu: [] };
}

function harness(results: (() => Promise<ScanResponse>)[]) {
  let session: string | null = 'gs_old';
  const renewed: string[] = [];
  const failures: unknown[] = [];
  let scans = 0;
  const renewal = createRenewal({
    currentSession: () => session,
    rescan: () => {
      scans += 1;
      return results.shift()!();
    },
    renewed: (response) => {
      session = response.sessionToken;
      renewed.push(response.sessionToken);
    },
    failed: (error) => failures.push(error),
  });
  return { renew: renewal.renew, renewal, renewed, failures, scans: () => scans };
}

describe('createRenewal', () => {
  it('手上的 session 被拒：重換成功，交出新的讓呼叫重發', async () => {
    const h = harness([() => Promise.resolve(scanned('gs_new'))]);
    await expect(h.renew('gs_old')).resolves.toBe('gs_new');
    expect(h.renewed).toEqual(['gs_new']);
    expect(h.failures).toEqual([]);
  });

  it('重換時後端說這間房不能用：交給失敗處理，畫面換成無法使用頁', async () => {
    const vacant = new ApiError(409, { code: 'room_vacant', message: 'x' });
    const h = harness([() => Promise.reject(vacant)]);
    await expect(h.renew('gs_old')).rejects.toBe(vacant);
    expect(h.failures).toEqual([vacant]);
  });

  it('換到的 session 重發又被拒：交給失敗處理，不再重換', async () => {
    const h = harness([() => Promise.resolve(scanned('gs_new'))]);
    await h.renew('gs_old');
    const rejected = new ApiError(401, { code: 'unauthorized', message: 'x' });
    h.renewal.renewedSessionRejected(rejected);
    expect(h.scans()).toBe(1);
    expect(h.failures).toEqual([rejected]);
  });

  it('同時被拒的幾個呼叫共用同一次重換', async () => {
    const h = harness([() => Promise.resolve(scanned('gs_new'))]);
    const [a, b] = await Promise.all([h.renew('gs_old'), h.renew('gs_old')]);
    expect([a, b]).toEqual(['gs_new', 'gs_new']);
    expect(h.scans()).toBe(1);
  });

  it('被拒的是已經換掉的舊 session：直接拿手上的新的，不再換', async () => {
    const h = harness([() => Promise.resolve(scanned('gs_new'))]);
    await h.renew('gs_old');
    await expect(h.renew('gs_old')).resolves.toBe('gs_new');
    expect(h.scans()).toBe(1);
  });
});
