import { useState } from 'react';
import type { GuestQuota } from './api/types';
import { estimateQuota, type FormNotice } from './lib/quota';
import { resultNotice, type SubmitResult } from './lib/submit';

export function useQuotaGuard(quota: GuestQuota | null) {
  const [notice, setNotice] = useState<FormNotice | null>(null);

  const check = (chosen: number): boolean => {
    const blocked = estimateQuota(chosen, quota);
    setNotice(blocked);
    return blocked === null;
  };

  const report = (result: SubmitResult | null) => {
    if (result !== null) setNotice(resultNotice(result));
  };

  const dismiss = () => setNotice(null);

  return { notice, check, report, dismiss };
}
