import type { TFunction } from 'i18next';

export interface ConflictContext {
  resolution?: unknown;
  actorName?: unknown;
  dept?: unknown;
  roomNo?: unknown;
}

export type ConflictAction = 'assign' | 'answer';

const TAIL_KEYS = {
  assign: 'dispatch.conflict.assignTail',
  answer: 'dispatch.conflict.answerTail',
} as const satisfies Record<ConflictAction, string>;

export function conflictText(
  context: ConflictContext,
  roomNo: string,
  action: ConflictAction,
  t: TFunction,
): { title: string; body: string } {
  const shownRoomNo = stringOr(context.roomNo, roomNo);
  const name = stringOr(context.actorName, '');
  const { kind, head } = conflictHead(context, shownRoomNo, name, t);
  return {
    title: t(TITLE_KEYS[kind]),
    body: t('dispatch.conflict.body', { head, tail: t(TAIL_KEYS[action]) }),
  };
}

const TITLE_KEYS = {
  assigned: 'dispatch.conflict.assigned.title',
  answered: 'dispatch.conflict.answered.title',
  guestWithdrew: 'dispatch.conflict.guestWithdrew.title',
  other: 'dispatch.conflict.other.title',
} as const;

function conflictHead(
  context: ConflictContext,
  roomNo: string,
  name: string,
  t: TFunction,
): { kind: keyof typeof TITLE_KEYS; head: string } {
  switch (context.resolution) {
    case 'assigned':
      if (context.dept === 'housekeeping' || context.dept === 'engineering') {
        const dept = t(`board.card.depts.${context.dept}`);
        return { kind: 'assigned', head: t('dispatch.conflict.assigned.head', { name, roomNo, dept }) };
      }
      break;
    case 'answered':
      return { kind: 'answered', head: t('dispatch.conflict.answered.head', { name, roomNo }) };
    case 'guest_withdrew':
      return { kind: 'guestWithdrew', head: t('dispatch.conflict.guestWithdrew.head', { roomNo }) };
  }
  return { kind: 'other', head: t('dispatch.conflict.other.head', { roomNo }) };
}

function stringOr(value: unknown, fallback: string): string {
  return typeof value === 'string' && value !== '' ? value : fallback;
}
