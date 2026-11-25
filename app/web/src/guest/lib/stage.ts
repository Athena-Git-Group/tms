import { awaitsReview } from '../../lib/awaiting-review';
import type { Dept, GuestTask, RequestState } from '../api/types';

export type StageTone = 'neutral' | 'accepted' | 'doing' | 'done';
export type StageStep = 0 | 1 | 2 | 3;
export type StageSection = 'active' | 'ended';

const LABEL = {
  submitted: 'guest.stage.submitted',
  accepted: 'guest.stage.accepted',
  processing: 'guest.stage.processing',
  completed: 'guest.stage.completed',
  cancelled: 'guest.stage.cancelled',
} as const;

export type StageLabelKey = (typeof LABEL)[keyof typeof LABEL];

export type StepSubKey =
  | 'guest.step.waitingDesk'
  | 'guest.step.deptReceived'
  | 'guest.step.startsWhenTaken'
  | 'guest.step.deptTaken'
  | 'guest.step.deptProcessing'
  | 'guest.step.rateWhenDone';

export type HomeCardKey =
  | 'guest.homeCard.triage'
  | 'guest.homeCard.received'
  | 'guest.homeCard.taken'
  | 'guest.homeCard.processing';

type StateMessage = 'triage' | 'completed' | 'cancelled';

export interface StageRow {
  labelKey: StageLabelKey;
  tone: StageTone;
  step: StageStep | null;
  section: StageSection;
  cancellable: boolean;
  locked: boolean;
  ratable: boolean;
  message: StateMessage | null;
  stepSubs: readonly [null, StepSubKey | null, StepSubKey | null, StepSubKey | null];
  homeCard: HomeCardKey | null;
}

const SUBMITTED: StageRow = {
  labelKey: LABEL.submitted,
  tone: 'neutral',
  step: 0,
  section: 'active',
  cancellable: true,
  locked: false,
  ratable: false,
  message: 'triage',
  stepSubs: [null, 'guest.step.waitingDesk', 'guest.step.startsWhenTaken', 'guest.step.rateWhenDone'],
  homeCard: 'guest.homeCard.triage',
};
const RECEIVED: StageRow = {
  labelKey: LABEL.accepted,
  tone: 'accepted',
  step: 1,
  section: 'active',
  cancellable: true,
  locked: false,
  ratable: false,
  message: null,
  stepSubs: [null, 'guest.step.deptReceived', 'guest.step.startsWhenTaken', 'guest.step.rateWhenDone'],
  homeCard: 'guest.homeCard.received',
};
const TAKEN: StageRow = {
  ...RECEIVED,
  cancellable: false,
  locked: true,
  stepSubs: [null, 'guest.step.deptReceived', 'guest.step.deptTaken', 'guest.step.rateWhenDone'],
  homeCard: 'guest.homeCard.taken',
};
const PROCESSING: StageRow = {
  labelKey: LABEL.processing,
  tone: 'doing',
  step: 2,
  section: 'active',
  cancellable: false,
  locked: true,
  ratable: false,
  message: null,
  stepSubs: [null, 'guest.step.deptReceived', 'guest.step.deptProcessing', 'guest.step.rateWhenDone'],
  homeCard: 'guest.homeCard.processing',
};
const COMPLETED: StageRow = {
  labelKey: LABEL.completed,
  tone: 'done',
  step: 3,
  section: 'ended',
  cancellable: false,
  locked: false,
  ratable: true,
  message: 'completed',
  stepSubs: [null, 'guest.step.deptReceived', null, null],
  homeCard: null,
};
const CANCELLED: StageRow = {
  labelKey: LABEL.cancelled,
  tone: 'neutral',
  step: null,
  section: 'ended',
  cancellable: false,
  locked: false,
  ratable: false,
  message: 'cancelled',
  stepSubs: [null, null, null, null],
  homeCard: null,
};

export const STAGE_TABLE: Readonly<Record<RequestState, StageRow>> = {
  triage: SUBMITTED,
  pool: RECEIVED,
  accepted: TAKEN,
  deferred: TAKEN,
  doing: PROCESSING,
  interrupted: PROCESSING,
  done: COMPLETED,
  inspected: COMPLETED,
  closed: COMPLETED,
  cancelled: CANCELLED,
};

const DEPT_LABEL = {
  housekeeping: 'guest.dept.housekeeping',
  engineering: 'guest.dept.engineering',
  frontdesk: 'guest.dept.frontdesk',
} as const satisfies Record<Dept, string>;

export type DeptLabelKey = (typeof DEPT_LABEL)[Dept];

export interface TextRef<K extends string = string> {
  key: K;
  deptKey: DeptLabelKey;
  createdAt: string;
}

type Staged = Pick<GuestTask, 'state' | 'endedAt'>;

export function stageOf(task: Staged): StageRow {
  return awaitsReview(task) ? PROCESSING : STAGE_TABLE[task.state];
}

export function deptLabelKey(dept: Dept | null | undefined): DeptLabelKey {
  return DEPT_LABEL[dept ?? 'frontdesk'];
}

export function canRate(task: Pick<GuestTask, 'state' | 'endedAt' | 'rating' | 'answer'>): boolean {
  return stageOf(task).ratable && task.rating == null && task.answer == null;
}

export function showsProgress(task: Pick<GuestTask, 'state' | 'endedAt' | 'answer'>): boolean {
  return stageOf(task).step !== null && task.answer == null;
}

export type MessageKind = StateMessage | 'answer';

export type MessageBody = { key: `guest.message.${StateMessage}.body` } | { text: string };

export interface MessageBoxView {
  kind: MessageKind;
  titleKey: `guest.message.${MessageKind}.title`;
  body: MessageBody;
}

export function messageBox(task: Pick<GuestTask, 'state' | 'endedAt' | 'answer'>): MessageBoxView | null {
  const message = stageOf(task).message;
  if (message !== 'cancelled' && task.answer != null) {
    return { kind: 'answer', titleKey: 'guest.message.answer.title', body: { text: task.answer } };
  }
  if (message === null) return null;
  return { kind: message, titleKey: `guest.message.${message}.title`, body: { key: `guest.message.${message}.body` } };
}

export type StepLook = 'done' | 'current' | 'todo';

export interface StepView {
  titleKey: StageLabelKey;
  look: StepLook;
  sub: TextRef<StepSubKey> | null;
}

export const STEP_TITLES = [LABEL.submitted, LABEL.accepted, LABEL.processing, LABEL.completed] as const;

export function stepView(task: Pick<GuestTask, 'state' | 'endedAt' | 'answer' | 'dept' | 'createdAt'>): StepView[] | null {
  const row = stageOf(task);
  if (row.step === null || !showsProgress(task)) return null;
  const current = row.step;
  const allDone = row.section === 'ended';
  const deptKey = deptLabelKey(task.dept);
  return STEP_TITLES.map((titleKey, index) => {
    const subKey = row.stepSubs[index];
    return {
      titleKey,
      look: allDone || index < current ? 'done' : index === current ? 'current' : 'todo',
      sub: subKey ? { key: subKey, deptKey, createdAt: task.createdAt } : null,
    };
  });
}

export function homeCardLine(task: Pick<GuestTask, 'state' | 'endedAt' | 'dept' | 'createdAt'>): TextRef<HomeCardKey> | null {
  const key = stageOf(task).homeCard;
  return key ? { key, deptKey: deptLabelKey(task.dept), createdAt: task.createdAt } : null;
}

export function latestActive<T extends Staged>(tasks: readonly T[]): T | null {
  return tasks.find((task) => stageOf(task).section === 'active') ?? null;
}

const SECTION_ORDER: readonly StageSection[] = ['active', 'ended'];

export type RequestSection<T> = { section: StageSection; tasks: T[] };

export function splitSections<T extends Staged>(tasks: readonly T[]): RequestSection<T>[] {
  return SECTION_ORDER.map((section) => ({ section, tasks: tasks.filter((task) => stageOf(task).section === section) })).filter(
    (group) => group.tasks.length > 0,
  );
}

export type HomeRequestsSection<T> =
  | { kind: 'none' }
  | { kind: 'card'; task: T; count: number }
  | { kind: 'idle'; count: number };

export function homeRequestsSection<T extends Staged>(tasks: readonly T[]): HomeRequestsSection<T> {
  if (tasks.length === 0) return { kind: 'none' };
  const task = latestActive(tasks);
  return task ? { kind: 'card', task, count: tasks.length } : { kind: 'idle', count: tasks.length };
}
