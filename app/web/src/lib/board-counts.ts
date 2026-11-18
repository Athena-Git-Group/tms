import type { DeskTask } from '../api/types';
import { columnOf, sortInColumn, type BoardColumn } from './board-columns';
import { waitingMinutes, waitTone } from './wait-pill';

export type TileTone = 'gray' | 'orange' | 'red';

export interface LongestWaiting {
  task: DeskTask;
  minutes: number | undefined;
  tone: TileTone;
}

export interface TileCounts {
  triage: number;
  pool: number;
  poolByDept: { housekeeping: number; engineering: number };
  inProgress: number;
  longest: LongestWaiting | null;
}

export interface ColumnWarning {
  kind: 'overdue' | 'awaitingInspection';
  count: number;
}

export interface ColumnHead {
  column: BoardColumn;
  tasks: DeskTask[];
  count: number;
  warning: ColumnWarning | null;
}

export const OVERDUE_MINUTES = 20;

function inColumns(tasks: readonly DeskTask[], ...columns: BoardColumn[]): DeskTask[] {
  return tasks.filter((task) => columns.includes(columnOf(task)));
}

function longestWaiting(hotelTasks: readonly DeskTask[], now: Date | undefined): LongestWaiting | null {
  const task = sortInColumn('pool', inColumns(hotelTasks, 'triage', 'pool'))[0];
  if (!task) {
    return null;
  }
  const minutes = now ? (waitingMinutes(task, now) ?? undefined) : undefined;
  const tone = waitTone(minutes ?? 0);
  return { task, minutes, tone: tone === 'green' ? 'gray' : tone };
}

export function tileCounts(hotelTasks: readonly DeskTask[], now: Date | undefined): TileCounts {
  const pool = inColumns(hotelTasks, 'pool');
  return {
    triage: inColumns(hotelTasks, 'triage').length,
    pool: pool.length,
    poolByDept: {
      housekeeping: pool.filter((task) => task.dept === 'housekeeping').length,
      engineering: pool.filter((task) => task.dept === 'engineering').length,
    },
    inProgress: inColumns(hotelTasks, 'accepted', 'doing').length,
    longest: longestWaiting(hotelTasks, now),
  };
}

function columnWarning(column: BoardColumn, tasks: readonly DeskTask[], now: Date | undefined): ColumnWarning | null {
  let kind: ColumnWarning['kind'];
  let count: number;
  if ((column === 'triage' || column === 'pool') && now) {
    kind = 'overdue';
    count = tasks.filter((task) => (waitingMinutes(task, now) ?? 0) >= OVERDUE_MINUTES).length;
  } else if (column === 'done') {
    kind = 'awaitingInspection';
    count = tasks.filter((task) => task.type === 'cleaning').length;
  } else {
    return null;
  }
  return count > 0 ? { kind, count } : null;
}

export function columnHeads(
  searchedTasks: readonly DeskTask[],
  columns: readonly BoardColumn[],
  now: Date | undefined,
): ColumnHead[] {
  return columns.map((column) => {
    const tasks = sortInColumn(column, inColumns(searchedTasks, column));
    return { column, tasks, count: tasks.length, warning: columnWarning(column, tasks, now) };
  });
}
