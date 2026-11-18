import type { DeskTask, DeskTaskDetail } from '../api/types';
import type { DisplayText } from './display-text';
import { clockTime, minutesSince } from './wait-pill';

export type StatusTagTone = 'yellow' | 'brand-soft' | 'gray' | 'brand' | 'green' | 'red';

export interface StatusTag {
  text: DisplayText;
  tone: StatusTagTone;
}

export type StatusCardTone = 'green' | 'brand-soft' | 'red' | 'white';

export interface StatusCard {
  title: DisplayText;
  description: DisplayText | null;
  tone: StatusCardTone;
}

export function statusTag(task: DeskTask): StatusTag {
  switch (task.state) {
    case 'triage':
      return tag('detail.tags.triage', 'yellow');
    case 'pool':
      return tag('detail.tags.pool', 'brand-soft');
    case 'accepted':
      return tag('detail.tags.accepted', 'gray');
    case 'doing':
      return tag('detail.tags.doing', 'brand');
    case 'done':
      if (task.answered) {
        return tag('detail.tags.answered', 'green');
      }
      if (task.type === 'repair') {
        return tag('detail.tags.repaired', 'green');
      }
      if (task.type === 'cleaning') {
        return tag('detail.tags.awaitingInspection', 'green');
      }
      return tag('detail.tags.done', 'green');
    case 'inspected':
      if (task.inspection === 'waived') {
        return tag('detail.tags.waived', 'green');
      }
      return tag('detail.tags.inspected', 'green');
    case 'closed':
      return tag('detail.tags.closed', 'green');
    case 'interrupted':
      return tag('detail.tags.interrupted', 'red');
    case 'deferred':
      return tag('detail.tags.deferred', 'gray');
    case 'cancelled':
      return tag('detail.tags.cancelled', 'gray');
  }
}

export function statusCard(task: DeskTaskDetail, now: Date, serverUtcOffsetMinutes: number): StatusCard | null {
  const time = (at: string) => clockTime(at, serverUtcOffsetMinutes);
  if (task.answered) {
    return card('green', { key: 'detail.answeredTitle' }, {
      key: 'detail.answeredBody',
      values: { answer: task.answer ?? '', name: task.answeredByName ?? '' },
    });
  }
  switch (task.state) {
    case 'triage':
      return null;
    case 'pool':
      return card(
        'brand-soft',
        { key: 'detail.cards.poolTitle', values: { dept: { key: `board.card.depts.${task.dept}` } } },
        { key: 'detail.cards.poolBody', values: { minutes: minutesSince(task.waitingSince ?? task.createdAt, now) } },
      );
    case 'accepted':
      return card(
        'white',
        { key: 'detail.cards.acceptedTitle', values: { name: task.assigneeName ?? '' } },
        acceptedDescription(task, time),
      );
    case 'doing': {
      const startedAt = task.startedAt ?? task.updatedAt;
      return card(
        'green',
        { key: 'detail.cards.doingTitle', values: { name: task.assigneeName ?? '', minutes: minutesSince(startedAt, now) } },
        { key: 'detail.cards.doingBody', values: { time: time(startedAt) } },
      );
    }
    case 'done':
      if (task.type === 'repair') {
        return card('green', { key: 'detail.cards.repairedTitle' }, null);
      }
      if (task.type === 'cleaning') {
        return card('green', { key: 'detail.cards.awaitingInspectionTitle' }, { key: 'detail.cards.awaitingInspectionBody' });
      }
      return card('green', { key: 'detail.cards.doneTitle' }, {
        key: 'detail.cards.doneBody',
        values: { time: time(task.doneAt ?? task.updatedAt) },
      });
    case 'inspected':
      if (task.inspection === 'waived') {
        return card('green', { key: 'detail.cards.waivedTitle' }, { key: 'detail.cards.waivedBody' });
      }
      return card('green', { key: 'detail.cards.inspectedTitle' }, { key: 'detail.cards.inspectedBody' });
    case 'closed':
      if (task.repair?.resolutionType === 'no_action_needed') {
        return card('green', { key: 'detail.cards.closedNoActionTitle' }, null);
      }
      if (task.repair?.verifiedByName) {
        return card('green', { key: 'detail.cards.closedVerifiedTitle', values: { name: task.repair.verifiedByName } }, null);
      }
      return card('green', { key: 'detail.cards.closedTitle' }, null);
    case 'interrupted':
      return interruptedCard(task);
    case 'deferred':
      if (task.deferral?.bySelf) {
        return card(
          'white',
          { key: 'detail.cards.deferredSelfTitle', values: { name: task.deferral.byName ?? task.assigneeName ?? '' } },
          { key: 'detail.cards.reasonBody', values: { reason: task.deferral.reason || '—' } },
        );
      }
      return card('white', { key: 'detail.cards.deferredTitle' }, {
        key: 'detail.cards.deferredBody',
        values: { name: task.deferral?.byName ?? '' },
      });
    case 'cancelled':
      return card('white', { key: 'detail.cards.cancelledTitle' }, cancelledDescription(task, time));
  }
}

function tag(key: DisplayText['key'], tone: StatusTagTone): StatusTag {
  return { text: { key }, tone };
}

function card(tone: StatusCardTone, title: DisplayText, description: DisplayText | null): StatusCard {
  return { title, description, tone };
}

function interruptedCard(task: DeskTaskDetail): StatusCard {
  const name = task.assigneeName ?? '';
  if (task.interruption?.bySelf) {
    return card(
      'red',
      { key: 'detail.cards.pausedTitle', values: { name } },
      { key: 'detail.cards.reasonBody', values: { reason: task.interruption.reason || '—' } },
    );
  }
  return card(
    'red',
    { key: 'detail.cards.leaveTitle', values: { name } },
    { key: 'detail.cards.leaveBody', values: { note: task.interruption?.handoverNote || '—' } },
  );
}

function acceptedDescription(task: DeskTaskDetail, time: (at: string) => string): DisplayText | null {
  const via = task.accepted?.via ?? task.acceptedVia;
  const at = task.accepted?.at ?? task.acceptedAt ?? task.updatedAt;
  const byName = task.accepted?.byName ?? '';
  switch (via) {
    case 'plan_assigned':
      return { key: 'detail.cards.acceptedByPlan' };
    case 'self_claimed':
      return { key: 'detail.cards.acceptedByClaim', values: { time: time(at) } };
    case 'manager_assigned':
      return { key: 'detail.cards.acceptedByManager', values: { name: byName, time: time(at) } };
    case 'reassigned':
      return {
        key: 'detail.cards.acceptedByReassign',
        values: { name: byName, time: time(at), previous: task.accepted?.previousAssigneeName ?? '' },
      };
    default:
      return null;
  }
}

function cancelledDescription(task: DeskTaskDetail, time: (at: string) => string): DisplayText | null {
  const at = time(task.endedAt ?? task.updatedAt);
  switch (task.cancellation?.reason) {
    case 'manager_void':
      return { key: 'detail.cards.cancelledByManager', values: { name: task.cancellation.byName ?? '', time: at } };
    case 'guest_withdrew':
      return { key: 'detail.cards.cancelledByGuest' };
    case 'plan_superseded':
      return { key: 'detail.cards.cancelledByPlan' };
    case 'staff_withdrew':
      return {
        key: 'detail.cards.cancelledByStaff',
        values: { name: task.cancellation.byName ?? task.createdByName ?? '', time: at },
      };
    default:
      return null;
  }
}
