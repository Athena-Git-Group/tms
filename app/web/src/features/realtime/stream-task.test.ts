import { describe, expect, it } from 'vitest';
import type { DeskTask } from '../../api/types';
import { parseStreamTask } from './stream-task';

const card: DeskTask = {
  id: 't-412',
  seq: 4,
  displayNo: 'T-0412',
  state: 'accepted',
  answered: false,
  dept: 'housekeeping',
  type: 'amenity',
  source: 'guest',
  roomNo: '412',
  floor: 4,
  vip: false,
  title: '加一條浴巾',
  guestTyped: false,
  assigneeName: '小美',
  createdAt: '2026-11-18T06:00:00Z',
  acceptedAt: '2026-11-18T06:05:00Z',
  acceptedVia: 'self_claimed',
  updatedAt: '2026-11-18T06:05:00Z',
  interruption: null,
};

const base = { stream_pos: 7, task_id: 't-412', seq: 4, type: 'claimed', card_seq: 4, business_date: '2026-11-18' };

describe('parseStreamTask', () => {
  it('upsert 帶卡片', () => {
    expect(parseStreamTask(JSON.stringify({ ...base, board: 'upsert', card }))).toEqual({
      kind: 'card',
      push: { taskId: 't-412', seq: 4, cardSeq: 4, businessDate: '2026-11-18', change: { kind: 'upsert', card } },
    });
  });

  it('remove 不帶卡片', () => {
    expect(parseStreamTask(JSON.stringify({ ...base, board: 'remove' }))).toEqual({
      kind: 'card',
      push: {
        taskId: 't-412',
        seq: 4,
        cardSeq: 4,
        businessDate: '2026-11-18',
        change: { kind: 'removedForBusinessDay' },
      },
    });
  });

  it.each([
    ['沒有 board', { stream_pos: 7, task_id: 't-412', seq: 4, type: 'claimed' }],
    ['board 是看不懂的值', { ...base, board: 'replace', card }],
    ['upsert 沒帶卡片', { ...base, board: 'upsert' }],
    ['卡片不是這張單', { ...base, board: 'upsert', card: { ...card, id: 't-999' } }],
    ['卡片步數和 card_seq 對不起來', { ...base, board: 'upsert', card: { ...card, seq: 3 } }],
    ['卡片缺欄位', { ...base, board: 'upsert', card: { id: 't-412', seq: 4, state: 'accepted' } }],
    ['卡片狀態是看不懂的值', { ...base, board: 'upsert', card: { ...card, state: 'teleported' } }],
    ['卡片選填欄位型別不對', { ...base, board: 'upsert', card: { ...card, sop: { done: '1', total: 3 } } }],
    ['沒有 business_date', { ...base, business_date: undefined, board: 'remove' }],
  ])('%s：要整份重讀，仍認得是哪一張單', (_name, payload) => {
    expect(parseStreamTask(JSON.stringify(payload))).toEqual({ kind: 'noCard', taskId: 't-412' });
  });

  it.each(['{not json', '[]', 'null'])('data 不是物件（%s）：要整份重讀', (data) => {
    expect(parseStreamTask(data)).toEqual({ kind: 'noCard', taskId: null });
  });
});
