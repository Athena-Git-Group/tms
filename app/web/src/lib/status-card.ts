import type { DeskTask, DeskTaskDetail } from '../api/types';
import type { zhTW } from '../i18n/locales/zh-TW';
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
    const answer = task.answer ?? '';
    const name = task.answeredByName ?? '';
    return card('green', (t) => t('detail.answeredTitle'), (t) => t('detail.answeredBody', { answer, name }));
  }
  switch (task.state) {
    case 'triage':
      return null;
    case 'pool':
      return card(
        'brand-soft',
        (t) => t('detail.cards.poolTitle', { dept: t(`board.card.depts.${task.dept}`) }),
        (t) => t('detail.cards.poolBody', { minutes: minutesSince(task.waitingSince ?? task.createdAt, now) }),
      );
    case 'accepted':
      return card(
        'white',
        (t) => t('detail.cards.acceptedTitle', { name: task.assigneeName ?? '' }),
        acceptedDescription(task, time),
      );
    case 'doing': {
      const startedAt = task.startedAt ?? task.updatedAt;
      return card(
        'green',
        (t) => t('detail.cards.doingTitle', { name: task.assigneeName ?? '', minutes: minutesSince(startedAt, now) }),
        (t) => t('detail.cards.doingBody', { time: time(startedAt) }),
      );
    }
    case 'done':
      if (task.type === 'repair') {
        return card('green', (t) => t('detail.cards.repairedTitle'), null);
      }
      if (task.type === 'cleaning') {
        return card(
          'green',
          (t) => t('detail.cards.awaitingInspectionTitle'),
          (t) => t('detail.cards.awaitingInspectionBody'),
        );
      }
      return card(
        'green',
        (t) => t('detail.cards.doneTitle'),
        (t) => t('detail.cards.doneBody', { time: time(task.doneAt ?? task.updatedAt) }),
      );
    case 'inspected':
      if (task.inspection === 'waived') {
        return card('green', (t) => t('detail.cards.waivedTitle'), (t) => t('detail.cards.waivedBody'));
      }
      return card('green', (t) => t('detail.cards.inspectedTitle'), (t) => t('detail.cards.inspectedBody'));
    case 'closed':
      if (task.repair?.resolutionType === 'no_action_needed') {
        return card('green', (t) => t('detail.cards.closedNoActionTitle'), null);
      }
      if (task.repair?.verifiedByName) {
        const name = task.repair.verifiedByName;
        return card('green', (t) => t('detail.cards.closedVerifiedTitle', { name }), null);
      }
      return card('green', (t) => t('detail.cards.closedTitle'), null);
    case 'interrupted':
      return interruptedCard(task);
    case 'deferred':
      if (task.deferral?.bySelf) {
        const name = task.deferral.byName ?? task.assigneeName ?? '';
        const reason = task.deferral.reason || '—';
        return card(
          'white',
          (t) => t('detail.cards.deferredSelfTitle', { name }),
          (t) => t('detail.cards.reasonBody', { reason }),
        );
      }
      const deferredBy = task.deferral?.byName ?? '';
      return card(
        'white',
        (t) => t('detail.cards.deferredTitle'),
        (t) => t('detail.cards.deferredBody', { name: deferredBy }),
      );
    case 'cancelled':
      return card('white', (t) => t('detail.cards.cancelledTitle'), cancelledDescription(task, time));
  }
}

function tag(key: `detail.tags.${keyof typeof zhTW.detail.tags}`, tone: StatusTagTone): StatusTag {
  return { text: (t) => t(key), tone };
}

function card(tone: StatusCardTone, title: DisplayText, description: DisplayText | null): StatusCard {
  return { title, description, tone };
}

function interruptedCard(task: DeskTaskDetail): StatusCard {
  const name = task.assigneeName ?? '';
  if (task.interruption?.bySelf) {
    const reason = task.interruption.reason || '—';
    return card(
      'red',
      (t) => t('detail.cards.pausedTitle', { name }),
      (t) => t('detail.cards.reasonBody', { reason }),
    );
  }
  const note = task.interruption?.handoverNote || '—';
  return card(
    'red',
    (t) => t('detail.cards.leaveTitle', { name }),
    (t) => t('detail.cards.leaveBody', { note }),
  );
}

function acceptedDescription(task: DeskTaskDetail, time: (at: string) => string): DisplayText | null {
  const via = task.accepted?.via ?? task.acceptedVia;
  const at = task.accepted?.at ?? task.acceptedAt ?? task.updatedAt;
  const byName = task.accepted?.byName ?? '';
  switch (via) {
    case 'plan_assigned':
      return (t) => t('detail.cards.acceptedByPlan');
    case 'self_claimed':
      return (t) => t('detail.cards.acceptedByClaim', { time: time(at) });
    case 'manager_assigned':
      return (t) => t('detail.cards.acceptedByManager', { name: byName, time: time(at) });
    case 'reassigned': {
      const previous = task.accepted?.previousAssigneeName ?? '';
      return (t) => t('detail.cards.acceptedByReassign', { name: byName, time: time(at), previous });
    }
    default:
      return null;
  }
}

function cancelledDescription(task: DeskTaskDetail, time: (at: string) => string): DisplayText | null {
  const at = time(task.endedAt ?? task.updatedAt);
  const byName = task.cancellation?.byName;
  switch (task.cancellation?.reason) {
    case 'manager_void':
      return (t) => t('detail.cards.cancelledByManager', { name: byName ?? '', time: at });
    case 'guest_withdrew':
      return (t) => t('detail.cards.cancelledByGuest');
    case 'plan_superseded':
      return (t) => t('detail.cards.cancelledByPlan');
    case 'staff_withdrew':
      return (t) => t('detail.cards.cancelledByStaff', { name: byName ?? task.createdByName ?? '', time: at });
    default:
      return null;
  }
}
