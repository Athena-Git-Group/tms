import type { DeskTask } from '../api/types';
import { columnOf } from './board-columns';
import type { DisplayText } from './display-text';

export type WaitPillTone = 'gray' | 'orange' | 'red' | 'green';

export interface WaitPill {
  text: DisplayText;
  tone: WaitPillTone;
}

export function waitingMinutes(task: DeskTask, now: Date): number | null {
  if (task.state !== 'triage' && task.state !== 'pool') {
    return null;
  }
  return minutesSince(task.waitingSince ?? task.createdAt, now);
}

export function waitTone(minutes: number): WaitPillTone {
  if (minutes >= 20) {
    return 'red';
  }
  return minutes >= 10 ? 'orange' : 'gray';
}

export const POOL_OVERDUE_MINUTES = 10;

export function minutesSince(at: string, now: Date): number {
  return Math.max(0, Math.floor((now.getTime() - Date.parse(at)) / 60_000));
}

export function clockTime(at: string, offsetMinutes: number): string {
  const time = new Date(Date.parse(at) + offsetMinutes * 60_000);
  return `${String(time.getUTCHours()).padStart(2, '0')}:${String(time.getUTCMinutes()).padStart(2, '0')}`;
}

export function waitPill(task: DeskTask, now: Date, serverUtcOffsetMinutes: number): WaitPill {
  const column = columnOf(task);
  switch (column) {
    case 'triage':
    case 'pool': {
      const minutes = waitingMinutes(task, now) ?? 0;
      const tone = waitTone(minutes);
      if (minutes < 1) {
        return { text: (t) => t('board.pill.justNow'), tone };
      }
      if (column === 'pool' && minutes >= POOL_OVERDUE_MINUTES) {
        return { text: (t) => t('board.pill.overdue', { minutes: minutes - POOL_OVERDUE_MINUTES }), tone };
      }
      return { text: (t) => t('board.pill.waiting', { minutes }), tone };
    }
    case 'accepted':
      if (task.acceptedVia === 'plan_assigned') {
        return { text: (t) => t('board.pill.planned'), tone: 'gray' };
      }
      return {
        text: (t) => t('board.pill.accepted', { minutes: minutesSince(task.acceptedAt ?? task.updatedAt, now) }),
        tone: 'gray',
      };
    case 'doing':
      return {
        text: (t) => t('board.pill.doing', { minutes: minutesSince(task.startedAt ?? task.updatedAt, now) }),
        tone: 'gray',
      };
    case 'interrupted':
      return {
        text: (t) => t('board.pill.stopped', { minutes: minutesSince(task.interruptedAt ?? task.updatedAt, now) }),
        tone: 'red',
      };
    case 'deferred':
      return { text: (t) => t('board.pill.deferred'), tone: 'gray' };
    case 'done':
      return {
        text: (t) => t('board.pill.at', { time: clockTime(task.doneAt ?? task.updatedAt, serverUtcOffsetMinutes) }),
        tone: 'green',
      };
    case 'closed':
      return {
        text: (t) => t('board.pill.at', { time: clockTime(task.endedAt ?? task.updatedAt, serverUtcOffsetMinutes) }),
        tone: 'gray',
      };
  }
}
