import { describe, expect, it } from 'vitest';
import { ApiError, NetworkError } from '../api/client';
import type { DeskBoard, DeskTask } from '../api/types';
import {
  answerFailureNotice,
  assignFailureNotice,
  assignRequest,
  assumedAssigned,
  undoFailureNotice,
  withConflictSettled,
  withTaskReplaced,
} from './dispatch';

function task(id: string, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id,
    displayNo: `T-${id}`,
    state: 'triage',
    answered: false,
    dept: 'frontdesk',
    type: 'other',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '房間有怪聲',
    guestTyped: true,
    createdAt: '2026-10-26T06:00:00Z',
    updatedAt: '2026-10-26T06:00:00Z',
    ...overrides,
  };
}

function board(tasks: DeskTask[]): DeskBoard {
  return { tasks } as unknown as DeskBoard;
}

const target = { roomNo: '305', dept: 'engineering' } as const;

describe('withTaskReplaced', () => {
  it('同一張單換成新的樣子，其他單不動、順序不變', () => {
    const before = board([task('a'), task('b'), task('c')]);
    const after = withTaskReplaced(before, task('b', { state: 'pool', dept: 'engineering' }));
    expect(after.tasks.map((item) => item.id)).toEqual(['a', 'b', 'c']);
    expect(after.tasks[1]).toMatchObject({ state: 'pool', dept: 'engineering' });
    expect(after.tasks[0]).toBe(before.tasks[0]);
  });

  it('看板裡沒有這張單時原樣回傳', () => {
    const before = board([task('a')]);
    expect(withTaskReplaced(before, task('z'))).toBe(before);
  });
});

describe('assignRequest', () => {
  it('via 原樣帶給後端，按鈕與拖曳各一', () => {
    expect(assignRequest('housekeeping', 'button')).toEqual({ dept: 'housekeeping', via: 'button' });
    expect(assignRequest('engineering', 'drag')).toEqual({ dept: 'engineering', via: 'drag' });
  });
});

describe('assumedAssigned', () => {
  it('先畫成那個部門的待認領', () => {
    expect(assumedAssigned(task('a'), 'housekeeping')).toMatchObject({ state: 'pool', dept: 'housekeeping' });
  });
});

describe('undoFailureNotice', () => {
  it('已被同仁認領：帶認領人、房號與後端說的部門', () => {
    const error = new ApiError(409, {
      code: 'undo_claimed',
      message: '',
      context: { assigneeName: '阿強', dept: 'housekeeping', roomNo: '305' },
    });
    expect(undoFailureNotice(error, target)).toEqual({
      kind: 'undoClaimed',
      name: '阿強',
      roomNo: '305',
      dept: 'housekeeping',
    });
  });

  it('已被住客收回', () => {
    const error = new ApiError(409, { code: 'undo_guest_withdrew', message: '', context: { roomNo: '305' } });
    expect(undoFailureNotice(error, target)).toEqual({ kind: 'undoGuestWithdrew', roomNo: '305' });
  });

  it('請求沒送到後端：說這張單還在浮條上的那個部門', () => {
    expect(undoFailureNotice(new NetworkError(new TypeError('failed')), target)).toEqual({
      kind: 'undoNotSent',
      roomNo: '305',
      dept: 'engineering',
    });
  });

  it('其他原因不跳對話框', () => {
    const error = new ApiError(409, { code: 'undo_expired', message: '', context: { roomNo: '305' } });
    expect(undoFailureNotice(error, target)).toBeNull();
  });
});

describe('assignFailureNotice', () => {
  function conflict(context: Record<string, unknown>) {
    return new ApiError(409, { code: 'task_not_in_triage', message: '', context });
  }

  it('撞單：帶後端回的 context 與房號，由 conflictText 組字', () => {
    const context = { resolution: 'assigned', actorName: '何志偉', dept: 'engineering', roomNo: '305' };
    expect(assignFailureNotice(conflict(context), '306')).toEqual({ kind: 'assignConflict', roomNo: '306', context });
  });

  it('不是撞單的失敗（例如斷線）：單還在待派單', () => {
    expect(assignFailureNotice(new NetworkError(new TypeError('failed')), '305')).toEqual({
      kind: 'assignNotSent',
      roomNo: '305',
    });
  });

  it('登入過期交給統一的處理，不跳對話框', () => {
    expect(assignFailureNotice(new ApiError(401, { code: 'unauthorized', message: '' }), '305')).toBeNull();
  });
});

describe('withConflictSettled', () => {
  const before = board([task('a', { roomNo: '305' }), task('b', { roomNo: '402' })]);

  it('被派走：照後端說的部門進待認領', () => {
    const after = withConflictSettled(before, 'b', { resolution: 'assigned', dept: 'engineering' });
    expect(after.tasks[1]).toMatchObject({ id: 'b', state: 'pool', dept: 'engineering' });
    expect(after.tasks[0]).toBe(before.tasks[0]);
  });

  it('被當場回答：結案、記為當場回答', () => {
    const after = withConflictSettled(before, 'a', { resolution: 'answered' });
    expect(after.tasks[0]).toMatchObject({ id: 'a', state: 'done', answered: true });
  });

  it('住客收回：取消', () => {
    const after = withConflictSettled(before, 'a', { resolution: 'guest_withdrew' });
    expect(after.tasks[0]).toMatchObject({ id: 'a', state: 'cancelled' });
  });

  it('派走卻沒帶合法部門、或後端沒說是哪一種：看板不動，等重讀', () => {
    expect(withConflictSettled(before, 'b', { resolution: 'assigned', dept: 'frontdesk' })).toBe(before);
    expect(withConflictSettled(before, 'b', {})).toBe(before);
  });

  it('看板裡沒有這張單：原樣回傳', () => {
    expect(withConflictSettled(before, 'zzz', { resolution: 'answered' })).toBe(before);
  });
});

describe('answerFailureNotice', () => {
  it('撞單：帶後端回的 context 與房號，由 conflictText 組字', () => {
    const context = { resolution: 'answered', actorName: '林佩君', roomNo: '305' };
    const error = new ApiError(409, { code: 'task_not_in_triage', message: '', context });
    expect(answerFailureNotice(error, '305')).toEqual({ kind: 'answerConflict', roomNo: '305', context });
  });

  it('不是撞單的失敗（例如斷線）：回覆還沒送出，單還在待派單', () => {
    expect(answerFailureNotice(new NetworkError(new TypeError('failed')), '305')).toEqual({
      kind: 'answerNotSent',
      roomNo: '305',
    });
  });

  it('其他錯誤碼（例如回覆不合規則）也當成沒有送出，不當撞單', () => {
    const error = new ApiError(422, { code: 'answer_invalid', message: '' });
    expect(answerFailureNotice(error, '305')).toEqual({ kind: 'answerNotSent', roomNo: '305' });
  });

  it('登入過期交給統一的處理，不跳對話框', () => {
    expect(answerFailureNotice(new ApiError(401, { code: 'unauthorized', message: '' }), '305')).toBeNull();
  });
});
