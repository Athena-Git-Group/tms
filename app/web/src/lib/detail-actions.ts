import type { DeskTask, StaffRole, TaskState } from '../api/types';

/** 「單據管理」動作列上的入口：取消、延後…、指派…（還沒人接）、轉派…（已有人接）。 */
export type ManagementAction = 'cancel' | 'defer' | 'assign' | 'reassign';

/** 動作列上分兩種看法：櫃檯，以及主管與值班主管（兩者看到的一樣）。 */
type ViewerGroup = 'frontdesk' | 'manager';

/** 進得了櫃檯畫面的角色各歸哪一種看法；房務、工務、老闆進不了登入後的畫面，不在表上。 */
const VIEWER_GROUP: Partial<Record<StaffRole, ViewerGroup>> = {
  frontdesk: 'frontdesk',
  manager: 'manager',
  duty_manager: 'manager',
};

/**
 * 每種狀態下，櫃檯與主管各看得到哪幾顆，依畫面上由左到右的順序；空陣列就是整列不出現。
 * 對照 `派單區與單據管理動作列.feature` 的動作列表格、`ui/ui-plan.md` 的角色表與雛形 `ui/desk.js` 的 `renderDetail`。
 * 已完成、已結案欄的單（`done`、`inspected`、`closed`）都沒有；待派單的單底部是派單區，也沒有動作列。
 */
const MANAGEMENT_ACTIONS: Readonly<Record<TaskState, Readonly<Record<ViewerGroup, readonly ManagementAction[]>>>> = {
  triage:      { frontdesk: [],                  manager: [] },                               // 待派單
  pool:        { frontdesk: ['cancel', 'defer'], manager: ['cancel', 'defer', 'assign'] },   // 待認領
  accepted:    { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] }, // 已接
  doing:       { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] }, // 進行中
  interrupted: { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] }, // 中斷
  deferred:    { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] }, // 延後中
  done:        { frontdesk: [],                  manager: [] },                               // 已完成（待查房）
  inspected:   { frontdesk: [],                  manager: [] },                               // 已查房
  closed:      { frontdesk: [],                  manager: [] },                               // 已結案
  cancelled:   { frontdesk: [],                  manager: [] },                               // 已取消
};

/** 詳情面板只有待派單的單有底部的「派單」區（派給房務、派給工務、當場回答…）。 */
export function hasDispatchArea(task: Pick<DeskTask, 'state'>): boolean {
  return task.state === 'triage';
}

/**
 * 「單據管理」動作列依登入者的角色與單的狀態畫哪幾顆，規則查 `MANAGEMENT_ACTIONS`。
 * 表外的例外：已當場回答的單、還不知道角色、進不了櫃檯畫面的角色，都沒有動作列。
 * 這一版這些入口都只畫出來，點了沒有反應。
 */
export function managementActions(
  role: StaffRole | undefined,
  task: Pick<DeskTask, 'state' | 'answered'>,
): readonly ManagementAction[] {
  const group = role && VIEWER_GROUP[role];
  if (!group || task.answered) return [];
  return MANAGEMENT_ACTIONS[task.state][group];
}
