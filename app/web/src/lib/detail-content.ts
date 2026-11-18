import type { TFunction } from 'i18next';
import type { DeskTaskDetail } from '../api/types';
import { clockTime, minutesSince } from './wait-pill';

/**
 * 單筆詳情面板「內容」那一塊的文字組法。寫成不碰畫面的純函式，畫面文字由呼叫端傳進來的 `t` 轉成當前語言，
 * 方便用單元測試逐一驗每一列、每一種情形。
 */

/** 「內容」裡的一列：左邊的名稱與右邊的值，都已經是當前語言的字。 */
export interface DetailContentRow {
  label: string;
  value: string;
}

/** 後端的日期（`2026-10-25`）寫成「10/25」。 */
function monthDay(date: string): string {
  const [, month, day] = date.split('-');
  return `${Number(month)}/${Number(day)}`;
}

/**
 * 「內容」的建立列和白卡灰字的「{多久前}建立」共用這一句，兩處才不會對同一張單寫出不同的分鐘數。
 * `now` 要傳用後端時間校正過的現在（見 `server-clock.ts`）；面板剛打開、時間還沒校正好時，畫面先寫「剛剛」。
 */
export function createdAgoText(task: DeskTaskDetail, now: Date | undefined, t: TFunction): string {
  const minutes = now ? minutesSince(task.createdAt, now) : 0;
  return minutes < 1 ? t('detail.justNow') : t('detail.minutesAgo', { minutes });
}

/** 負責人：有人負責寫名字；沒有人時，待認領的單寫「等同仁認領」，其他寫「—」。 */
function assigneeText(task: DeskTaskDetail, t: TFunction): string {
  if (task.assigneeName != null) {
    return task.assigneeName;
  }
  return task.state === 'pool' ? t('detail.content.awaitingClaim') : t('detail.content.noAssignee');
}

/** 來源：住客掃碼、系統排房只有來源名稱；櫃檯代開、同仁報修在括號裡寫開單的人。 */
function sourceText(task: DeskTaskDetail, t: TFunction): string {
  if (task.source === 'desk' || task.source === 'staff') {
    return t(`detail.content.sources.${task.source}`, { name: task.createdByName ?? '' });
  }
  return t(`detail.content.sources.${task.source}`);
}

/**
 * 「內容」六列，由上到下：
 * 房號「{房號}（{樓層} 樓）」；住客「{名字} · {M/D}–{M/D}」，空房寫「空房」，不寫人數；
 * 部門「{部門} · {類型}」；負責人；來源；建立「{時:分}（{多久前}）」。
 * `now` 要傳用後端時間校正過的現在；`serverUtcOffsetMinutes` 是後端伺服器的時區偏移，建立的「時:分」照它顯示。
 */
export function detailContentRows(
  task: DeskTaskDetail,
  now: Date | undefined,
  serverUtcOffsetMinutes: number,
  t: TFunction,
): DetailContentRow[] {
  const guest = task.guest;
  return [
    {
      label: t('detail.content.room'),
      value: t('detail.content.roomValue', { roomNo: task.roomNo, floor: task.floor }),
    },
    {
      label: t('detail.content.guest'),
      value: guest
        ? t('detail.content.guestValue', {
            name: guest.name,
            checkin: monthDay(guest.checkinDate),
            checkout: monthDay(guest.checkoutDate),
          })
        : t('detail.content.vacant'),
    },
    {
      label: t('detail.content.dept'),
      value: t('detail.content.deptValue', {
        dept: t(`board.card.depts.${task.dept}`),
        type: t(`board.card.types.${task.type}`),
      }),
    },
    { label: t('detail.content.assignee'), value: assigneeText(task, t) },
    { label: t('detail.content.source'), value: sourceText(task, t) },
    {
      label: t('detail.content.created'),
      value: t('detail.content.createdValue', {
        time: clockTime(task.createdAt, serverUtcOffsetMinutes),
        ago: createdAgoText(task, now, t),
      }),
    },
  ];
}
