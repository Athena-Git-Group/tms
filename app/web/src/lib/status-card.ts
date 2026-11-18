import type { DeskTask, DeskTaskDetail } from '../api/types';
import type { DisplayText } from './display-text';
import { clockTime, minutesSince } from './wait-pill';

/**
 * 單筆詳情面板的狀態標籤與狀態訊息卡：依單的內容算出要顯示的文字與顏色。
 * 寫成不碰畫面的純計算，是為了能用單元測試逐一驗每一種標籤與訊息卡。
 *
 * 分成兩個函式，是因為兩者能算的時間點不同：標籤只需要看板清單就有的資料，面板一打開就能顯示；
 * 訊息卡要等單筆詳情讀回來才有足夠的內容。
 *
 * 兩者的文字與顏色都照 `ui/ui-plan.md` 畫面 8。
 */

/** 狀態標籤的六種顏色：黃、品牌淺底、灰、品牌色實底、淡綠、淡紅。實際的色碼由樣式決定。 */
export type StatusTagTone = 'yellow' | 'brand-soft' | 'gray' | 'brand' | 'green' | 'red';

export interface StatusTag {
  text: DisplayText;
  tone: StatusTagTone;
}

/** 狀態訊息卡的四種底色：淡綠、品牌淺底、淡紅、白。 */
export type StatusCardTone = 'green' | 'brand-soft' | 'red' | 'white';

export interface StatusCard {
  title: DisplayText;
  /** 有些狀況只有標題一句，沒有說明。 */
  description: DisplayText | null;
  tone: StatusCardTone;
}

/**
 * 這張單的狀態標籤。標籤只是既有十種狀態的顯示文字，有幾種狀態再依類型或結果細分：
 * 完成的修繕單是「已完修」、完成的打掃單是「待查房」；免查房結案的寫「免查房」；櫃檯當場回答的單（資料上是完成、記有回答）寫「已回答」。
 */
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

/**
 * 這張單的狀態訊息卡；不顯示訊息卡的單（待派單）回 `null`。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`）；
 * `serverUtcOffsetMinutes` 是後端伺服器的時區偏移，卡上的「時:分」照它顯示。
 *
 * 文字照 `spec.md` 使用者故事 5 的「狀態訊息卡」「已接的說明」兩張表，以及 `ui/ui-plan.md` 畫面 8 補的幾列；
 * 底色照畫面 8：當場回答、進行中與完成一類是淡綠，待認領是品牌淺底，中斷是淡紅，已接、延後中、已取消是白底。
 * 已接的單本版不判斷逾時，卡上不會出現逾時的字樣。
 */
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

/** 中斷的卡：同仁自己暫停的寫他填的原因；其餘當成請假，寫交代的做到哪，沒有交代時寫「—」。 */
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

/** 已接的說明：依這張單怎麼到這個人手上，班前排房、自己認領、從待認領被指派、被轉派各一句。 */
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

/** 已取消的說明，依取消的原因：主管取消、住客收回、排房被取代、同仁撤回報修。 */
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
      // 撤回報修的就是報修人；後端沒記撤回者時改用開單的人。
      return {
        key: 'detail.cards.cancelledByStaff',
        values: { name: task.cancellation.byName ?? task.createdByName ?? '', time: at },
      };
    default:
      return null;
  }
}
