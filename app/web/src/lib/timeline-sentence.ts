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
    return name ? (t) => t('detail.events.actors.guest', { name }) : (t) => t('detail.events.actors.guestUnnamed');
  }
  if (kind === 'system') {
    return (t) => t('detail.events.actors.system');
  }
  return (t) => t('detail.events.actors.staff', { name: name ?? '' });
}

function deptText(detail: EntryDetail, task: DeskTaskDetail): DisplayText {
  return (t) => t(`board.card.depts.${detail.dept ?? task.dept}`);
}

function createdSentence(entry: DeskTimelineEntry, detail: EntryDetail, task: DeskTaskDetail): TimelineSentence | null {
  const actor = actorText(entry);
  const dept = deptText(detail, task);
  if (entry.actor.kind === 'guest') {
    if (detail.guestTyped ?? task.guestTyped) {
      const text = task.guestText?.original || task.title;
      return { text: (t) => t('detail.events.guestTyped', { text }), actor, dot: 'brand' };
    }
    return { text: (t) => t('detail.events.guestPicked', { title: task.title, dept: dept(t) }), actor, dot: 'brand' };
  }
  if (task.source === 'desk') {
    return { text: (t) => t('detail.events.deskCreated', { title: task.title, dept: dept(t) }), actor, dot: 'purple' };
  }
  if (task.source === 'staff') {
    return { text: (t) => t('detail.events.staffReported', { title: task.title, dept: dept(t) }), actor, dot: 'brand' };
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
      const dept = deptText(detail, task);
      return { text: (t) => t(key, { dept: dept(t) }), actor, dot: 'purple' };
    }
    case 'answered':
      return { text: (t) => t('detail.events.answered', { answer: detail.answer ?? '' }), actor, dot: 'purple' };
    case 'assigned':
      if (entry.actor.kind !== 'system') return null;
      return {
        text: (t) => t('detail.events.planAssigned', { name: detail.assigneeName ?? '' }),
        actor: (t) => t('detail.events.actors.plan'),
        dot: 'brand',
      };
    case 'claimed':
      return {
        text: (t) => t('detail.events.claimed', { name: detail.assigneeName ?? entry.actor.name ?? '' }),
        actor,
        dot: 'brand',
      };
    case 'reassigned':
      return {
        text: (t) =>
          t('detail.events.reassigned', {
            name: detail.assigneeName ?? '',
            previous: detail.previousAssigneeName ?? '—',
          }),
        actor,
        dot: 'orange',
      };
    case 'started':
      return { text: (t) => t('detail.events.started'), actor, dot: 'brand' };
    case 'completed': {
      const awaitingInspection = task.type === 'cleaning' && task.inspection !== 'waived';
      const key = awaitingInspection ? 'detail.events.completedAwaitingInspection' : 'detail.events.completed';
      return { text: (t) => t(key), actor, dot: 'green' };
    }
    case 'inspection_passed':
      return { text: (t) => t('detail.events.inspectionPassed'), actor, dot: 'green' };
    case 'repair_verified':
      return { text: (t) => t('detail.events.repairVerified'), actor, dot: 'green' };
    case 'interrupted':
      if (detail.bySelf) {
        return { text: (t) => t('detail.events.paused', { reason: detail.reason ?? '—' }), actor, dot: 'orange' };
      }
      return {
        text: (t) => t('detail.events.leaveInterrupted', { name: task.assigneeName ?? '' }),
        actor,
        dot: 'orange',
      };
    case 'deferred':
      if (detail.bySelf) return null;
      return { text: (t) => t('detail.events.deferred'), actor, dot: 'orange' };
    case 'cancelled': {
      if (!detail.cancelReason) return null;
      const key = CANCEL_KEYS[detail.cancelReason];
      return { text: (t) => t(key), actor, dot: 'orange' };
    }
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
    return (t) => t('detail.events.meta', { time, actor: actor(t) });
  }
  const [, month, date] = day.split('-');
  return (t) => t('detail.events.metaWithDate', { date: `${Number(month)}/${Number(date)}`, time, actor: actor(t) });
}
