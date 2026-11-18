import type { DeskTask, StaffRole, TaskState } from '../api/types';

export type ManagementAction = 'cancel' | 'defer' | 'assign' | 'reassign';

type ViewerGroup = 'frontdesk' | 'manager';

const VIEWER_GROUP: Partial<Record<StaffRole, ViewerGroup>> = {
  frontdesk: 'frontdesk',
  manager: 'manager',
  duty_manager: 'manager',
};

const MANAGEMENT_ACTIONS: Readonly<Record<TaskState, Readonly<Record<ViewerGroup, readonly ManagementAction[]>>>> = {
  triage:      { frontdesk: [],                  manager: [] },
  pool:        { frontdesk: ['cancel', 'defer'], manager: ['cancel', 'defer', 'assign'] },
  accepted:    { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] },
  doing:       { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] },
  interrupted: { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] },
  deferred:    { frontdesk: [],                  manager: ['cancel', 'defer', 'reassign'] },
  done:        { frontdesk: [],                  manager: [] },
  inspected:   { frontdesk: [],                  manager: [] },
  closed:      { frontdesk: [],                  manager: [] },
  cancelled:   { frontdesk: [],                  manager: [] },
};

export function hasDispatchArea(task: Pick<DeskTask, 'state'>): boolean {
  return task.state === 'triage';
}

export function managementActions(
  role: StaffRole | undefined,
  task: Pick<DeskTask, 'state' | 'answered'>,
): readonly ManagementAction[] {
  const group = role && VIEWER_GROUP[role];
  if (!group || task.answered) return [];
  return MANAGEMENT_ACTIONS[task.state][group];
}
