import type { TFunction } from 'i18next';

/**
 * 撞單對話框的字：標題與內文都看後端回的 `task_not_in_triage` 的 `context`，
 * 寫成不碰畫面的純函式，派單與當場回答共用（畫面在 `features/dispatch/ConflictDialog.tsx`），畫面文字由呼叫端傳進來的 `t` 轉成當前語言。
 */

/** 後端回的 `context`：`resolution` 說這張單怎麼離開待派單（被派走、被當場回答、住客收回、其他）。 */
export interface ConflictContext {
  resolution?: unknown;
  actorName?: unknown;
  dept?: unknown;
  roomNo?: unknown;
}

/** 撞單時櫃檯剛才想做的事：派單或當場回答。內文最後一句跟著它換。 */
export type ConflictAction = 'assign' | 'answer';

/** 內文最後一句：派單是「…不需要再派單。」，當場回答是「…不需要再回覆。」。 */
const TAIL_KEYS = {
  assign: 'dispatch.conflict.assignTail',
  answer: 'dispatch.conflict.answerTail',
} as const satisfies Record<ConflictAction, string>;

/**
 * 撞單對話框的標題與內文。
 * 第一句看這張單怎麼離開待派單（`context.resolution`），最後一句看剛才想做什麼（`action`）。
 * 房號以後端回的為準，沒有時用 `roomNo`；派走卻沒帶合法的部門、或後端沒說是哪一種，只說已經不在待派單。
 */
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
