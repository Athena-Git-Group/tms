import type { DeskTaskDetail, DeskTimelineEntry } from '../api/types';
import type { DisplayText } from './display-text';
import { dateAtUtcOffset } from './server-clock';
import { clockTime } from './wait-pill';

export type TimelineDotTone = 'brand' | 'gray' | 'orange' | 'purple' | 'green';

export interface TimelineSentence {
  text: DisplayText;
  actor: DisplayText;
  dot: TimelineDotTone;
}

type EntryDetail = NonNullable<DeskTimelineEntry['detail']>;

function actorText(entry: DeskTimelineEntry): DisplayText {
  const { kind, name } = entry.actor;
  if (kind === 'guest') {
    return name ? { key: 'detail.events.actors.guest', values: { name } } : { key: 'detail.events.actors.guestUnnamed' };
  }
  if (kind === 'system') {
    return { key: 'detail.events.actors.system' };
  }
  return { key: 'detail.events.actors.staff', values: { name: name ?? '' } };
}

function deptText(detail: EntryDetail, task: DeskTaskDetail): DisplayText {
  return { key: `board.card.depts.${detail.dept ?? task.dept}` };
}

function createdSentence(entry: DeskTimelineEntry, detail: EntryDetail, task: DeskTaskDetail): TimelineSentence | null {
  const actor = actorText(entry);
  const dept = deptText(detail, task);
  if (entry.actor.kind === 'guest') {
    if (detail.guestTyped ?? task.guestTyped) {
      const text = task.guestText?.original || task.title;
      return { text: { key: 'detail.events.guestTyped', values: { text } }, actor, dot: 'brand' };
    }
    return { text: { key: 'detail.events.guestPicked', values: { title: task.title, dept } }, actor, dot: 'brand' };
  }
  if (task.source === 'desk') {
    return { text: { key: 'detail.events.deskCreated', values: { title: task.title, dept } }, actor, dot: 'purple' };
  }
  if (task.source === 'staff') {
    return { text: { key: 'detail.events.staffReported', values: { title: task.title, dept } }, actor, dot: 'brand' };
  }
  return null;
}

const CANCEL_KEYS = {
  guest_withdrew: 'detail.events.cancelled.guestWithdrew',
  manager_void: 'detail.events.cancelled.managerVoid',
  plan_superseded: 'detail.events.cancelled.planSuperseded',
  staff_withdrew: 'detail.events.cancelled.staffWithdrew',
} as const;

export function timelineSentence(entry: DeskTimelineEntry, task: DeskTaskDetail): TimelineSentence | null {
  const detail: EntryDetail = entry.detail ?? {};
  const actor = actorText(entry);
  switch (entry.type) {
    case 'created':
      return createdSentence(entry, detail, task);
    case 'triaged': {
      const key = detail.via === 'drag' ? 'detail.events.triagedByDrag' : 'detail.events.triaged';
      return { text: { key, values: { dept: deptText(detail, task) } }, actor, dot: 'purple' };
    }
    case 'answered':
      return { text: { key: 'detail.events.answered', values: { answer: detail.answer ?? '' } }, actor, dot: 'purple' };
    case 'assigned':
      if (entry.actor.kind !== 'system') return null;
      return {
        text: { key: 'detail.events.planAssigned', values: { name: detail.assigneeName ?? '' } },
        actor: { key: 'detail.events.actors.plan' },
        dot: 'brand',
      };
    case 'claimed':
      return {
        text: { key: 'detail.events.claimed', values: { name: detail.assigneeName ?? entry.actor.name ?? '' } },
        actor,
        dot: 'brand',
      };
    case 'reassigned':
      return {
        text: {
          key: 'detail.events.reassigned',
          values: { name: detail.assigneeName ?? '', previous: detail.previousAssigneeName ?? '—' },
        },
        actor,
        dot: 'orange',
      };
    case 'started':
      return { text: { key: 'detail.events.started' }, actor, dot: 'brand' };
    case 'completed': {
      const awaitingInspection = task.type === 'cleaning' && task.inspection !== 'waived';
      const key = awaitingInspection ? 'detail.events.completedAwaitingInspection' : 'detail.events.completed';
      return { text: { key }, actor, dot: 'green' };
    }
    case 'inspection_passed':
      return { text: { key: 'detail.events.inspectionPassed' }, actor, dot: 'green' };
    case 'repair_verified':
      return { text: { key: 'detail.events.repairVerified' }, actor, dot: 'green' };
    case 'interrupted':
      if (detail.bySelf) {
        return { text: { key: 'detail.events.paused', values: { reason: detail.reason ?? '—' } }, actor, dot: 'orange' };
      }
      return {
        text: { key: 'detail.events.leaveInterrupted', values: { name: task.assigneeName ?? '' } },
        actor,
        dot: 'orange',
      };
    case 'deferred':
      if (detail.bySelf) return null;
      return { text: { key: 'detail.events.deferred' }, actor, dot: 'orange' };
    case 'cancelled':
      if (!detail.cancelReason) return null;
      return { text: { key: CANCEL_KEYS[detail.cancelReason] }, actor, dot: 'orange' };
    default:
      return null;
  }
}

export function timelineMeta(
  entry: DeskTimelineEntry,
  actor: DisplayText,
  now: Date | undefined,
  serverUtcOffsetMinutes: number,
): DisplayText {
  const time = clockTime(entry.at, serverUtcOffsetMinutes);
  const day = dateAtUtcOffset(new Date(Date.parse(entry.at)), serverUtcOffsetMinutes);
  if (!now || day === dateAtUtcOffset(now, serverUtcOffsetMinutes)) {
    return { key: 'detail.events.meta', values: { time, actor } };
  }
  const [, month, date] = day.split('-');
  return {
    key: 'detail.events.metaWithDate',
    values: { date: `${Number(month)}/${Number(date)}`, time, actor },
  };
}
