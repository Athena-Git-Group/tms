import type { DeskTaskDetail, DeskTimelineEntry } from '../api/types';
import type { DisplayText } from './display-text';
import { dateAtUtcOffset } from './server-clock';
import { clockTime } from './wait-pill';

/**
 * 單筆詳情面板的歷程：把後端回的一筆事件換成畫面上的一句話、操作者與左邊的色點。
 * 句子由畫面依事件的種類與內容組出來，四種介面語言才換得動；
 * 寫成不碰畫面的純計算，是為了能用單元測試逐一驗每一種事件的句子。
 *
 * 規格（`單筆詳情/歷程.feature`）還沒定句子的事件回 `null`，畫面略過那一筆，不自己編句子。
 * 被復原的派單與那一筆復原，後端已經不回，這裡不必處理。
 */

/** 歷程色點的五種顏色：品牌色、灰、橘、紫、綠。實際的色碼由樣式決定。 */
export type TimelineDotTone = 'brand' | 'gray' | 'orange' | 'purple' | 'green';

export interface TimelineSentence {
  text: DisplayText;
  /** 句子下面灰字裡的操作者。 */
  actor: DisplayText;
  dot: TimelineDotTone;
}

type EntryDetail = NonNullable<DeskTimelineEntry['detail']>;

/** 住客寫「住客（{名字}）」，空房沒有名字時只寫「住客」；系統寫「系統」；同仁寫名字。 */
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

/**
 * 系統排房建立的單回 `null`：櫃檯要知道的是排給了誰，那句寫在緊接著的指派事件上，建立這一筆再寫一次是重複。
 */
function createdSentence(entry: DeskTimelineEntry, detail: EntryDetail, task: DeskTaskDetail): TimelineSentence | null {
  const actor = actorText(entry);
  const dept = deptText(detail, task);
  if (entry.actor.kind === 'guest') {
    if (detail.guestTyped ?? task.guestTyped) {
      // 住客來電時櫃檯要對得上住客自己打的字，所以寫原文、不寫翻譯。
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

/**
 * 這一筆事件在歷程上的句子、操作者與色點；歷程上不顯示的事件回 `null`，畫面略過那一筆。
 * `task` 是這筆事件所屬的單，有些句子要用到單的標題、類型、來源或負責人。
 */
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
      // 主管從待認領指派的句子規格還沒定，先不顯示；系統指派就是班前排房。
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
      // 免查房的打掃單完成時已經結案，不會再等主管查房，句子跟其他單一樣。
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
      // 請假的中斷事件不記是誰請假，被中斷的就是這張單現在的負責人。
      return {
        text: { key: 'detail.events.leaveInterrupted', values: { name: task.assigneeName ?? '' } },
        actor,
        dot: 'orange',
      };
    case 'deferred':
      // 本人延後的句子規格還沒定，先不顯示。主管延後的原因屬於主管工作台，櫃檯只寫「延後」。
      if (detail.bySelf) return null;
      return { text: { key: 'detail.events.deferred' }, actor, dot: 'orange' };
    case 'cancelled':
      if (!detail.cancelReason) return null;
      return { text: { key: CANCEL_KEYS[detail.cancelReason] }, actor, dot: 'orange' };
    default:
      return null;
  }
}

/**
 * 句子下面的灰字：「{HH:mm} · {操作者}」；事件不是今天發生的，前面多寫日期「{M/D} {HH:mm} · {操作者}」，
 * 還沒結束的單可能拖過好幾天，只寫時刻會分不出是哪一天。
 * 時刻與「今天」都照後端伺服器的時區（`serverUtcOffsetMinutes`），櫃檯電腦時區設錯時才不會寫錯日期。
 * `now` 要傳用後端時間校正過的現在；還沒校正好時當成今天，只寫時刻。
 */
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
