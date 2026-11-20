import type { Dept, GuestTask, RequestState } from '../api/types';

/**
 * 後端十個狀態值對到住客看得懂的階段。首頁進度卡、我的需求清單、需求進度頁都從這張表與下面的函式拿結果，
 * 不各自判斷狀態值；三個畫面各寫一套的話，改一個狀態的顯示就要改三處，漏一處畫面就互相矛盾。
 * 文字一律回語系鍵與參數，畫面再翻成目前的語言。
 */

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

/** 不看回覆、只看狀態就決定的訊息框；櫃檯回覆蓋過已取消以外的這幾種。 */
type StateMessage = 'triage' | 'completed' | 'cancelled';

export interface StageRow {
  labelKey: StageLabelKey;
  tone: StageTone;
  /** 進度走到第幾步；已取消不顯示進度，是 `null`。 */
  step: StageStep | null;
  section: StageSection;
  cancellable: boolean;
  /** 同仁已經接手，住客不能自己取消，底部改成請打電話找櫃檯的說明。 */
  locked: boolean;
  /** 這個狀態可以評分；還要沒評過、沒有櫃檯回覆，見 `canRate`。 */
  ratable: boolean;
  message: StateMessage | null;
  /** 四步各自的副文，第 0 步永遠沒有。 */
  stepSubs: readonly [null, StepSubKey | null, StepSubKey | null, StepSubKey | null];
  /** 首頁進度卡的一行說明；不上進度卡的是 `null`。 */
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
/** 修繕結案由主管驗收，不請住客評分。 */
const CLOSED: StageRow = { ...COMPLETED, ratable: false };
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
  closed: CLOSED,
  cancelled: CANCELLED,
};

const DEPT_LABEL = {
  housekeeping: 'guest.dept.housekeeping',
  engineering: 'guest.dept.engineering',
  frontdesk: 'guest.dept.frontdesk',
} as const satisfies Record<Dept, string>;

export type DeptLabelKey = (typeof DEPT_LABEL)[Dept];

/** 一段要翻譯的文字：語系鍵，加上畫面要代入的部門與送出時間（用不到的參數翻譯時忽略）。 */
export interface TextRef<K extends string = string> {
  key: K;
  deptKey: DeptLabelKey;
  /** 送出時間的原始值，畫面自己格式化成「時:分」。 */
  createdAt: string;
}

export function stageOf(task: Pick<GuestTask, 'state'>): StageRow {
  return STAGE_TABLE[task.state];
}

/** 待派單時後端還沒定部門，住客看到的一律是櫃檯。 */
export function deptLabelKey(dept: Dept | null | undefined): DeptLabelKey {
  return DEPT_LABEL[dept ?? 'frontdesk'];
}

/** 櫃檯當場回答的單也是 `done`，但住客已經拿到答覆，不再請他評分。 */
export function canRate(task: Pick<GuestTask, 'state' | 'rating' | 'answer'>): boolean {
  return stageOf(task).ratable && task.rating == null && task.answer == null;
}

export function showsProgress(task: Pick<GuestTask, 'state' | 'answer'>): boolean {
  return stageOf(task).step !== null && task.answer == null;
}

export type MessageKind = StateMessage | 'answer';

/** 訊息框的內文：飯店設定的字用語系鍵，櫃檯回覆照原文。 */
export type MessageBody = { key: `guest.message.${StateMessage}.body` } | { text: string };

export interface MessageBoxView {
  kind: MessageKind;
  titleKey: `guest.message.${MessageKind}.title`;
  body: MessageBody;
}

/** 依序取第一條符合的：已取消、櫃檯回覆、待派單、已完成；都不符合就沒有訊息框。 */
export function messageBox(task: Pick<GuestTask, 'state' | 'answer'>): MessageBoxView | null {
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

/** 四步各自的樣子與副文；不顯示進度的（已取消、櫃檯已回覆）回 `null`。 */
export function stepView(task: Pick<GuestTask, 'state' | 'answer' | 'dept' | 'createdAt'>): StepView[] | null {
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

/** 首頁進度卡的一行說明；只有進行中的需求上進度卡，其餘回 `null`。 */
export function homeCardLine(task: Pick<GuestTask, 'state' | 'dept' | 'createdAt'>): TextRef<HomeCardKey> | null {
  const key = stageOf(task).homeCard;
  return key ? { key, deptKey: deptLabelKey(task.dept), createdAt: task.createdAt } : null;
}

/**
 * 新到舊。後端時鐘固定時同一次送出的幾張送出時間相同，這時照這支手機記下的送出先後，後送出的在上；
 * 不是這支手機送的（另一支手機、櫃檯代開）排在同一刻的最後，彼此維持後端給的順序。
 */
export function newestFirst<T extends Pick<GuestTask, 'id' | 'createdAt'>>(
  tasks: readonly T[],
  sentOrder: readonly string[] = [],
): T[] {
  const rank = (task: T) => sentOrder.indexOf(task.id);
  return [...tasks].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || rank(b) - rank(a));
}

/** 首頁進度卡要顯示的那一張：進行中裡送出時間最新的。 */
export function latestActive<T extends Pick<GuestTask, 'id' | 'state' | 'createdAt'>>(
  tasks: readonly T[],
  sentOrder: readonly string[] = [],
): T | null {
  return newestFirst(tasks, sentOrder).find((task) => stageOf(task).section === 'active') ?? null;
}

const SECTION_ORDER: readonly StageSection[] = ['active', 'ended'];

export type RequestSection<T> = { section: StageSection; tasks: T[] };

/** 清單的段落：先「進行中」再「已結束」，各自新到舊；沒有內容的段落不在結果裡，一張都沒有時是空陣列。 */
export function splitSections<T extends Pick<GuestTask, 'id' | 'state' | 'createdAt'>>(
  tasks: readonly T[],
  sentOrder: readonly string[] = [],
): RequestSection<T>[] {
  const sorted = newestFirst(tasks, sentOrder);
  return SECTION_ORDER.map((section) => ({ section, tasks: sorted.filter((task) => stageOf(task).section === section) })).filter(
    (group) => group.tasks.length > 0,
  );
}

export type HomeRequestsSection<T> =
  | { kind: 'none' }
  | { kind: 'card'; task: T; count: number }
  | { kind: 'idle'; count: number };

/** 首頁「我的需求」：一張都沒有時整塊不出現；有進行中的顯示最新那張的進度卡；全部結束時是「目前沒有進行中的需求」。 */
export function homeRequestsSection<T extends Pick<GuestTask, 'id' | 'state' | 'createdAt'>>(
  tasks: readonly T[],
  sentOrder: readonly string[] = [],
): HomeRequestsSection<T> {
  if (tasks.length === 0) return { kind: 'none' };
  const task = latestActive(tasks, sentOrder);
  return task ? { kind: 'card', task, count: tasks.length } : { kind: 'idle', count: tasks.length };
}
