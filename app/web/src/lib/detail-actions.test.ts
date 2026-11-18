import { describe, expect, it } from 'vitest';
import type { StaffRole, TaskState } from '../api/types';
import { hasDispatchArea, managementActions } from './detail-actions';

/**
 * 期待值照 `派單區與單據管理動作列.feature` 的動作列表格（`:79-93`、`:120-125`）
 * 與 `ui/ui-plan.md` 的角色表（`:437-439`），不照對照表抄：
 * 待認領櫃檯「取消」「延後…」、主管多「指派…」；已接、進行中、中斷、延後中只有主管看得到「取消」「延後…」「轉派…」；
 * 待派單（底部是派單區）、已完成、已結案欄、已取消都整列不出現。
 */
const NONE: string[] = [];
const FRONTDESK_POOL = ['cancel', 'defer'];
const MANAGER_POOL = ['cancel', 'defer', 'assign'];
const MANAGER_IN_HAND = ['cancel', 'defer', 'reassign'];

/** 對照表的每一列：狀態、櫃檯看到的、主管看到的。 */
const ROWS: [TaskState, string[], string[]][] = [
  ['triage', NONE, NONE],
  ['pool', FRONTDESK_POOL, MANAGER_POOL],
  ['accepted', NONE, MANAGER_IN_HAND],
  ['doing', NONE, MANAGER_IN_HAND],
  ['interrupted', NONE, MANAGER_IN_HAND],
  ['deferred', NONE, MANAGER_IN_HAND],
  ['done', NONE, NONE],
  ['inspected', NONE, NONE],
  ['closed', NONE, NONE],
  ['cancelled', NONE, NONE],
];

describe('managementActions 對照表', () => {
  it.each(ROWS)('%s：櫃檯看到 %j，主管與值班主管看到 %j', (state, frontdesk, manager) => {
    const task = { state, answered: false };
    expect(managementActions('frontdesk', task)).toEqual(frontdesk);
    expect(managementActions('manager', task)).toEqual(manager);
    expect(managementActions('duty_manager', task)).toEqual(manager);
  });
});

describe('managementActions 例外', () => {
  it.each(['frontdesk', 'manager', 'duty_manager'] as const)('已當場回答的單，%s 也沒有動作列', (role) => {
    expect(managementActions(role, { state: 'done', answered: true })).toEqual([]);
    expect(managementActions(role, { state: 'closed', answered: true })).toEqual([]);
  });

  it('還不知道角色時沒有動作列', () => {
    expect(managementActions(undefined, { state: 'pool', answered: false })).toEqual([]);
    expect(managementActions(undefined, { state: 'doing', answered: false })).toEqual([]);
  });

  it.each(['housekeeping', 'engineering', 'owner'] as StaffRole[])('進不了櫃檯畫面的 %s 沒有動作列', (role) => {
    expect(managementActions(role, { state: 'pool', answered: false })).toEqual([]);
    expect(managementActions(role, { state: 'doing', answered: false })).toEqual([]);
  });
});

describe('hasDispatchArea', () => {
  it('只有待派單的單有派單區', () => {
    expect(hasDispatchArea({ state: 'triage' })).toBe(true);
    expect(hasDispatchArea({ state: 'pool' })).toBe(false);
  });
});
