import type { TaskState } from '../api/types';

export function awaitsReview(task: { state: TaskState; endedAt?: string | null }): boolean {
  return task.state === 'done' && !task.endedAt;
}
