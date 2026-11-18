import type { TFunction } from 'i18next';
import type { DeskTaskDetail } from '../api/types';
import { clockTime, minutesSince } from './wait-pill';

export interface DetailContentRow {
  label: string;
  value: string;
}

function monthDay(date: string): string {
  const [, month, day] = date.split('-');
  return `${Number(month)}/${Number(day)}`;
}

export function createdAgoText(task: DeskTaskDetail, now: Date | undefined, t: TFunction): string {
  const minutes = now ? minutesSince(task.createdAt, now) : 0;
  return minutes < 1 ? t('detail.justNow') : t('detail.minutesAgo', { minutes });
}

function assigneeText(task: DeskTaskDetail, t: TFunction): string {
  if (task.assigneeName != null) {
    return task.assigneeName;
  }
  return task.state === 'pool' ? t('detail.content.awaitingClaim') : t('detail.content.noAssignee');
}

function sourceText(task: DeskTaskDetail, t: TFunction): string {
  if (task.source === 'desk' || task.source === 'staff') {
    return t(`detail.content.sources.${task.source}`, { name: task.createdByName ?? '' });
  }
  return t(`detail.content.sources.${task.source}`);
}

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
