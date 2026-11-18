import { describe, expect, it } from 'vitest';
import { parseStreamTask } from './stream-task';

const base = { stream_pos: 7, task_id: 't-412', seq: 4, type: 'claimed', card_seq: 4, business_date: '2026-11-18' };

describe('parseStreamTask', () => {
  it('upsert 帶卡片', () => {
    const card = { id: 't-412', seq: 4, state: 'accepted' };
    expect(parseStreamTask(JSON.stringify({ ...base, board: 'upsert', card }))).toEqual({
      kind: 'card',
      push: { taskId: 't-412', seq: 4, cardSeq: 4, businessDate: '2026-11-18', card },
    });
  });

  it('remove 不帶卡片', () => {
    expect(parseStreamTask(JSON.stringify({ ...base, board: 'remove' }))).toEqual({
      kind: 'card',
      push: { taskId: 't-412', seq: 4, cardSeq: 4, businessDate: '2026-11-18', card: null },
    });
  });

  it.each([
    ['沒有 board', { stream_pos: 7, task_id: 't-412', seq: 4, type: 'claimed' }],
    ['board 是看不懂的值', { ...base, board: 'replace', card: { id: 't-412', seq: 4 } }],
    ['upsert 沒帶卡片', { ...base, board: 'upsert' }],
    ['卡片不是這張單', { ...base, board: 'upsert', card: { id: 't-999', seq: 4 } }],
    ['卡片步數和 card_seq 對不起來', { ...base, board: 'upsert', card: { id: 't-412', seq: 3 } }],
    ['沒有 business_date', { ...base, business_date: undefined, board: 'remove' }],
  ])('%s：要整份重讀，仍認得是哪一張單', (_name, payload) => {
    expect(parseStreamTask(JSON.stringify(payload))).toEqual({ kind: 'noCard', taskId: 't-412' });
  });

  it.each(['{not json', '[]', 'null'])('data 不是物件（%s）：要整份重讀', (data) => {
    expect(parseStreamTask(data)).toEqual({ kind: 'noCard', taskId: null });
  });
});
