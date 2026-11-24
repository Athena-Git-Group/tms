import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { deptLabelKey } from '../lib/stage';
import { hhmm } from '../lib/time';

export function useRequestMeta(): (task: Pick<GuestTask, 'createdAt' | 'dept'>) => string {
  const { t } = useTranslation();
  return (task) => t('guest.request.meta', { time: hhmm(task.createdAt), dept: t(deptLabelKey(task.dept)) });
}
