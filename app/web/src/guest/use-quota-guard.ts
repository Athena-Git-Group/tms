import { useState } from 'react';
import type { GuestTask } from './api/types';
import { estimateQuota, type FormNotice } from './lib/quota';
import { resultNotice, type SubmitResult } from './lib/submit';

/**
 * 備品補充與設備報修表單下方的提示框：送出前依這次住宿的清單估算額度，擋下就顯示額度提示；
 * 送出停下來時顯示結果的提示；住客改了任何選擇就收掉。
 */
export function useQuotaGuard(tasks: readonly Pick<GuestTask, 'createdAt'>[]) {
  const [notice, setNotice] = useState<FormNotice | null>(null);

  /** 選了 `chosen` 項能不能送；不能送時顯示額度提示，能送時收掉上一次的提示。 */
  const check = (chosen: number): boolean => {
    const blocked = estimateQuota(chosen, tasks, new Date());
    setNotice(blocked);
    return blocked === null;
  };

  const report = (result: SubmitResult | null) => {
    if (result !== null) setNotice(resultNotice(result));
  };

  const dismiss = () => setNotice(null);

  return { notice, check, report, dismiss };
}
