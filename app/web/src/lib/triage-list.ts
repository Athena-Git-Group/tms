import type { DeskTask } from '../api/types';
import { columnOf, sortInColumn } from './board-columns';

export function triageTasks(tasks: readonly DeskTask[]): DeskTask[] {
  return sortInColumn(
    'triage',
    tasks.filter((task) => columnOf(task) === 'triage'),
  );
}

export function poolCount(tasks: readonly DeskTask[], dept: 'housekeeping' | 'engineering'): number {
  return tasks.filter((task) => columnOf(task) === 'pool' && task.dept === dept).length;
}
