import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { deptLabelKey } from '../lib/stage';
import { hhmm } from '../lib/time';

/** 「{送出時間} 送出 · {部門}」：畫面上唯一的時間，沒有接手人與各步時間。 */
export function useRequestMeta(): (task: Pick<GuestTask, 'createdAt' | 'dept'>) => string {
  const { t } = useTranslation();
  return (task) => t('guest.request.meta', { time: hhmm(task.createdAt), dept: t(deptLabelKey(task.dept)) });
}
