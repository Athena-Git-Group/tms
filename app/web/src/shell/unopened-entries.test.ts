import { describe, expect, it } from 'vitest';
import type { StaffRole } from '../api/types';
import { UNOPENED_ENTRIES, visibleUnopenedEntries } from './unopened-entries';

const labels = (position: Parameters<typeof visibleUnopenedEntries>[0], role: StaffRole | undefined) =>
  visibleUnopenedEntries(position, role).map((entry) => entry.labelKey);

describe('尚未開放的入口清單', () => {
  it('側欄導覽依序是通知板、遺失物，所有角色都看得到', () => {
    expect(labels('sidebarNav', 'frontdesk')).toEqual(['shell.noticeBoard', 'shell.lostAndFound']);
    expect(labels('sidebarNav', 'manager')).toEqual(['shell.noticeBoard', 'shell.lostAndFound']);
  });

  it('工具列的代開單所有角色都看得到', () => {
    expect(labels('toolbar', 'frontdesk')).toEqual(['shell.createRequest']);
    expect(labels('toolbar', 'manager')).toEqual(['shell.createRequest']);
  });

  it('還沒有登入資料時，只畫不限角色的入口', () => {
    expect(labels('sidebarNav', undefined)).toHaveLength(2);
    expect(labels('toolbar', undefined)).toHaveLength(1);
  });

  it('設定已經開放，不在清單裡', () => {
    expect(UNOPENED_ENTRIES.map((entry) => entry.labelKey)).toEqual([
      'shell.noticeBoard',
      'shell.lostAndFound',
      'shell.createRequest',
    ]);
  });
});
