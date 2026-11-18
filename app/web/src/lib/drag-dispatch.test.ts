import { describe, expect, it } from 'vitest';
import type { DeskTask, TaskState } from '../api/types';
import { canDragToDispatch, cellWhileDragging, dropDeptOf, dropZoneDepts } from './drag-dispatch';

function task(state: TaskState, overrides: Partial<DeskTask> = {}): DeskTask {
  return {
    id: '1',
    displayNo: 'T-1',
    seq: 1,
    state,
    answered: false,
    dept: 'frontdesk',
    type: 'other',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '房間有怪聲',
    guestTyped: true,
    createdAt: '2026-10-26T06:25:00Z',
    updatedAt: '2026-10-26T06:25:00Z',
    ...overrides,
  };
}

describe('canDragToDispatch', () => {
  it('只有待派單的單拖得動', () => {
    expect(canDragToDispatch(task('triage'))).toBe(true);
    for (const state of ['pool', 'accepted', 'doing', 'done', 'interrupted', 'deferred', 'inspected', 'closed', 'cancelled'] as const) {
      expect(canDragToDispatch(task(state))).toBe(false);
    }
  });
});

describe('dropZoneDepts', () => {
  const dragging = task('triage', { floor: 3 });

  it('不分泳道時待認領欄有給房務、給工務，房務在上', () => {
    expect(dropZoneDepts('pool', null, dragging)).toEqual(['housekeeping', 'engineering']);
  });

  it('待認領以外的欄沒有拖放區', () => {
    expect(dropZoneDepts('triage', null, dragging)).toEqual([]);
    expect(dropZoneDepts('accepted', { kind: 'floor', floor: 3 }, dragging)).toEqual([]);
  });

  it('部門泳道時各部門只有自己的拖放區，未分部門沒有', () => {
    expect(dropZoneDepts('pool', { kind: 'dept', dept: 'housekeeping' }, dragging)).toEqual(['housekeeping']);
    expect(dropZoneDepts('pool', { kind: 'dept', dept: 'engineering' }, dragging)).toEqual(['engineering']);
    expect(dropZoneDepts('pool', { kind: 'dept', dept: 'frontdesk' }, dragging)).toEqual([]);
  });

  it('樓層泳道時只有這張單那一層樓有兩個拖放區', () => {
    expect(dropZoneDepts('pool', { kind: 'floor', floor: 3 }, dragging)).toEqual(['housekeeping', 'engineering']);
    expect(dropZoneDepts('pool', { kind: 'floor', floor: 2 }, dragging)).toEqual([]);
  });
});

describe('cellWhileDragging', () => {
  const dragging = task('triage', { id: 'drag-1', floor: 3 });
  const other = task('accepted', { id: 'other-1' });

  it('沒有人在拖時不放拖放區、也不變淡', () => {
    expect(cellWhileDragging('pool', null, [], null)).toEqual({ zones: [], faded: false });
    expect(cellWhileDragging('accepted', null, [other], null)).toEqual({ zones: [], faded: false });
  });

  it('拖曳中有拖放區的格子不變淡', () => {
    expect(cellWhileDragging('pool', null, [], dragging)).toEqual({
      zones: ['housekeeping', 'engineering'],
      faded: false,
    });
  });

  it('拖曳中正在拖的那張單所在的格子不變淡', () => {
    expect(cellWhileDragging('triage', null, [dragging], dragging)).toEqual({ zones: [], faded: false });
  });

  it('拖曳中其他格子變淡，包括泳道裡沒有拖放區的待認領格', () => {
    expect(cellWhileDragging('accepted', null, [other], dragging)).toEqual({ zones: [], faded: true });
    expect(cellWhileDragging('pool', { kind: 'floor', floor: 2 }, [], dragging)).toEqual({ zones: [], faded: true });
  });
});

describe('dropDeptOf', () => {
  it('按 Esc 取消時不派，即使套件還記著拖放區', () => {
    expect(
      dropDeptOf({ canceled: true, targetDept: 'housekeeping', targetIsCurrent: true, releasedOverDept: 'housekeeping' }),
    ).toBeUndefined();
  });

  it('以放開那一點底下的拖放區為準', () => {
    expect(
      dropDeptOf({ canceled: false, targetDept: 'housekeeping', targetIsCurrent: true, releasedOverDept: 'engineering' }),
    ).toBe('engineering');
  });

  it('套件還記著途中滑過的工務、放開那一點在房務時，派給房務', () => {
    expect(
      dropDeptOf({ canceled: false, targetDept: 'engineering', targetIsCurrent: false, releasedOverDept: 'housekeeping' }),
    ).toBe('housekeeping');
  });

  it('手指移到拖放區馬上放開、套件還沒有落點時，看放開那一點底下的拖放區', () => {
    expect(
      dropDeptOf({ canceled: false, targetDept: undefined, targetIsCurrent: false, releasedOverDept: 'housekeeping' }),
    ).toBe('housekeeping');
  });

  it('放開那一點不在拖放區上、套件用放開那一點判斷卡片蓋在拖放區上時，派給那個拖放區', () => {
    expect(
      dropDeptOf({ canceled: false, targetDept: 'engineering', targetIsCurrent: true, releasedOverDept: undefined }),
    ).toBe('engineering');
  });

  it('移出拖放區馬上放開、套件還記著舊落點時不派', () => {
    expect(
      dropDeptOf({ canceled: false, targetDept: 'engineering', targetIsCurrent: false, releasedOverDept: undefined }),
    ).toBeUndefined();
  });

  it('在拖放區以外放開不派', () => {
    expect(
      dropDeptOf({ canceled: false, targetDept: undefined, targetIsCurrent: true, releasedOverDept: undefined }),
    ).toBeUndefined();
  });
});
