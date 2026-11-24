import { describe, expect, it } from 'vitest';
import type { GuestTask, RequestState } from '../api/types';
import {
  STAGE_TABLE,
  canRate,
  deptLabelKey,
  homeCardLine,
  homeRequestsSection,
  latestActive,
  messageBox,
  showsProgress,
  splitSections,
  stageOf,
  stepView,
} from './stage';

const ENDED_AT = '2026-11-20T07:00:00Z';
const ENDED_STATES: readonly RequestState[] = ['done', 'inspected', 'closed', 'cancelled'];

function task(overrides: Partial<GuestTask> = {}): GuestTask {
  const state = overrides.state ?? 'pool';
  return {
    id: 't1',
    type: 'amenity',
    state: 'pool',
    dept: 'housekeeping',
    title: '礦泉水 ×2',
    answer: null,
    rating: null,
    createdAt: '2026-11-20T06:05:00Z',
    endedAt: ENDED_STATES.includes(state) ? ENDED_AT : null,
    ...overrides,
  };
}

interface Expected {
  label: string;
  tone: string;
  section: 'active' | 'ended';
  cancellable: boolean;
  locked: boolean;
  ratable: boolean;
  message: string | null;
  looks: string[] | null;
  subs: (string | null)[] | null;
  homeCard: string | null;
}

const RECEIVED_SUBS = [null, 'guest.step.deptReceived', 'guest.step.startsWhenTaken', 'guest.step.rateWhenDone'];
const TAKEN_SUBS = [null, 'guest.step.deptReceived', 'guest.step.deptTaken', 'guest.step.rateWhenDone'];
const PROCESSING_SUBS = [null, 'guest.step.deptReceived', 'guest.step.deptProcessing', 'guest.step.rateWhenDone'];
const DONE_SUBS = [null, 'guest.step.deptReceived', null, null];
const ALL_DONE = ['done', 'done', 'done', 'done'];

const EXPECTED: Record<RequestState, Expected> = {
  triage: {
    label: 'guest.stage.submitted', tone: 'neutral', section: 'active', cancellable: true, locked: false,
    ratable: false, message: 'triage', looks: ['current', 'todo', 'todo', 'todo'],
    subs: [null, 'guest.step.waitingDesk', 'guest.step.startsWhenTaken', 'guest.step.rateWhenDone'],
    homeCard: 'guest.homeCard.triage',
  },
  pool: {
    label: 'guest.stage.accepted', tone: 'accepted', section: 'active', cancellable: true, locked: false,
    ratable: false, message: null, looks: ['done', 'current', 'todo', 'todo'], subs: RECEIVED_SUBS,
    homeCard: 'guest.homeCard.received',
  },
  accepted: {
    label: 'guest.stage.accepted', tone: 'accepted', section: 'active', cancellable: false, locked: true,
    ratable: false, message: null, looks: ['done', 'current', 'todo', 'todo'], subs: TAKEN_SUBS,
    homeCard: 'guest.homeCard.taken',
  },
  deferred: {
    label: 'guest.stage.accepted', tone: 'accepted', section: 'active', cancellable: false, locked: true,
    ratable: false, message: null, looks: ['done', 'current', 'todo', 'todo'], subs: TAKEN_SUBS,
    homeCard: 'guest.homeCard.taken',
  },
  doing: {
    label: 'guest.stage.processing', tone: 'doing', section: 'active', cancellable: false, locked: true,
    ratable: false, message: null, looks: ['done', 'done', 'current', 'todo'], subs: PROCESSING_SUBS,
    homeCard: 'guest.homeCard.processing',
  },
  interrupted: {
    label: 'guest.stage.processing', tone: 'doing', section: 'active', cancellable: false, locked: true,
    ratable: false, message: null, looks: ['done', 'done', 'current', 'todo'], subs: PROCESSING_SUBS,
    homeCard: 'guest.homeCard.processing',
  },
  done: {
    label: 'guest.stage.completed', tone: 'done', section: 'ended', cancellable: false, locked: false,
    ratable: true, message: 'completed', looks: ALL_DONE, subs: DONE_SUBS, homeCard: null,
  },
  inspected: {
    label: 'guest.stage.completed', tone: 'done', section: 'ended', cancellable: false, locked: false,
    ratable: true, message: 'completed', looks: ALL_DONE, subs: DONE_SUBS, homeCard: null,
  },
  closed: {
    label: 'guest.stage.completed', tone: 'done', section: 'ended', cancellable: false, locked: false,
    ratable: true, message: 'completed', looks: ALL_DONE, subs: DONE_SUBS, homeCard: null,
  },
  cancelled: {
    label: 'guest.stage.cancelled', tone: 'neutral', section: 'ended', cancellable: false, locked: false,
    ratable: false, message: 'cancelled', looks: null, subs: null, homeCard: null,
  },
};

const ALL_STATES = Object.keys(EXPECTED) as RequestState[];

describe('狀態對照表涵蓋後端每一個狀態', () => {
  it('表的鍵剛好是十個後端狀態', () => {
    expect(Object.keys(STAGE_TABLE).sort()).toEqual([...ALL_STATES].sort());
  });

  it.each(ALL_STATES)('%s', (state) => {
    const want = EXPECTED[state];
    const row = stageOf(task({ state }));
    const view = stepView(task({ state }));
    expect(row).toBe(STAGE_TABLE[state]);
    expect({
      label: row.labelKey,
      tone: row.tone,
      section: row.section,
      cancellable: row.cancellable,
      locked: row.locked,
      ratable: canRate(task({ state })),
      message: messageBox(task({ state }))?.kind ?? null,
      looks: view?.map((step) => step.look) ?? null,
      subs: view?.map((step) => step.sub?.key ?? null) ?? null,
      homeCard: homeCardLine(task({ state }))?.key ?? null,
    }).toEqual(want);
  });

  it('鎖定說明只出現在進行中而且不能取消的狀態', () => {
    for (const state of ALL_STATES) {
      const row = stageOf(task({ state }));
      expect(row.locked).toBe(row.section === 'active' && !row.cancellable);
    }
  });
});

describe('完成了還在等查房或驗收的單（沒有結束時刻）', () => {
  const awaiting = (dept: 'housekeeping' | 'engineering') => task({ state: 'done', dept, endedAt: null });

  it.each(['housekeeping', 'engineering'] as const)('%s 的單對住客還是處理中，跟做到一半一樣', (dept) => {
    const want = EXPECTED.doing;
    const row = stageOf(awaiting(dept));
    const view = stepView(awaiting(dept));
    expect({
      label: row.labelKey,
      tone: row.tone,
      section: row.section,
      cancellable: row.cancellable,
      locked: row.locked,
      ratable: canRate(awaiting(dept)),
      message: messageBox(awaiting(dept))?.kind ?? null,
      looks: view?.map((step) => step.look) ?? null,
      subs: view?.map((step) => step.sub?.key ?? null) ?? null,
      homeCard: homeCardLine(awaiting(dept))?.key ?? null,
    }).toEqual(want);
  });

  it('排在進行中那一段，首頁進度卡挑得到它', () => {
    const waiting = awaiting('housekeeping');
    expect(splitSections([waiting]).map((group) => group.section)).toEqual(['active']);
    expect(latestActive([waiting])).toBe(waiting);
    expect(homeRequestsSection([waiting])).toEqual({ kind: 'card', task: waiting, count: 1 });
  });

  it('查房或驗收通過、有了結束時刻才算已完成並可以評分', () => {
    const ended = task({ state: 'done', endedAt: ENDED_AT });
    expect(stageOf(ended).labelKey).toBe('guest.stage.completed');
    expect(canRate(ended)).toBe(true);
  });
});

describe('deptLabelKey', () => {
  it('三個部門各自的字，沒有部門時是櫃檯', () => {
    expect(deptLabelKey('housekeeping')).toBe('guest.dept.housekeeping');
    expect(deptLabelKey('engineering')).toBe('guest.dept.engineering');
    expect(deptLabelKey('frontdesk')).toBe('guest.dept.frontdesk');
    expect(deptLabelKey(null)).toBe('guest.dept.frontdesk');
  });
});

describe('櫃檯回覆與評分', () => {
  it('評過的不能再評', () => {
    expect(canRate(task({ state: 'done', rating: 4 }))).toBe(false);
  });

  it('櫃檯當場回答的不請住客評分、不顯示四步，訊息框是回覆原文', () => {
    const answered = task({ state: 'done', answer: '已請工務上去看' });
    expect(canRate(answered)).toBe(false);
    expect(showsProgress(answered)).toBe(false);
    expect(stepView(answered)).toBeNull();
    expect(messageBox(answered)).toEqual({
      kind: 'answer',
      titleKey: 'guest.message.answer.title',
      body: { text: '已請工務上去看' },
    });
  });

  it('櫃檯回覆蓋過待派單的訊息框', () => {
    expect(messageBox(task({ state: 'triage', answer: '好的' }))?.kind).toBe('answer');
  });

  it('只看狀態的訊息框帶語系鍵', () => {
    expect(messageBox(task({ state: 'triage' }))).toEqual({
      kind: 'triage',
      titleKey: 'guest.message.triage.title',
      body: { key: 'guest.message.triage.body' },
    });
  });

  it('已取消排第一，即使有櫃檯回覆', () => {
    expect(messageBox(task({ state: 'cancelled', answer: '好的' }))?.kind).toBe('cancelled');
  });
});

describe('副文與進度卡說明帶的參數', () => {
  it('工務的單副文帶工務', () => {
    expect(stepView(task({ state: 'doing', dept: 'engineering' }))?.[2]?.sub).toEqual({
      key: 'guest.step.deptProcessing',
      deptKey: 'guest.dept.engineering',
      createdAt: '2026-11-20T06:05:00Z',
    });
  });

  it('待認領的進度卡說明帶部門與送出時間', () => {
    expect(homeCardLine(task({ state: 'pool' }))).toEqual({
      key: 'guest.homeCard.received',
      deptKey: 'guest.dept.housekeeping',
      createdAt: '2026-11-20T06:05:00Z',
    });
  });
});

describe('排序', () => {
  const at = (minute: number) => `2026-11-20T06:${String(minute).padStart(2, '0')}:00Z`;

  it('分成進行中與已結束，各自照後端給的順序', () => {
    const tasks = [
      task({ id: 'd', state: 'closed', createdAt: at(3) }),
      task({ id: 'c', state: 'triage', createdAt: at(2) }),
      task({ id: 'b', state: 'cancelled', createdAt: at(1) }),
      task({ id: 'a', state: 'doing', createdAt: at(0) }),
    ];
    expect(splitSections(tasks).map((group) => [group.section, group.tasks.map((t) => t.id)])).toEqual([
      ['active', ['c', 'a']],
      ['ended', ['d', 'b']],
    ]);
  });

  it('不自己重排：同一刻送出的照後端給的先後', () => {
    const tasks = [
      task({ id: 'water', createdAt: at(5) }),
      task({ id: 'towel', createdAt: at(5) }),
      task({ id: 'older', createdAt: at(1) }),
    ];
    expect(splitSections(tasks)[0]!.tasks.map((t) => t.id)).toEqual(['water', 'towel', 'older']);
  });

  it('沒有內容的段落不在結果裡，一張都沒有時是空的', () => {
    expect(splitSections([task({ id: 'a', state: 'done', createdAt: at(0) })]).map((group) => group.section)).toEqual(['ended']);
    expect(splitSections([task({ id: 'a', state: 'pool', createdAt: at(0) })]).map((group) => group.section)).toEqual(['active']);
    expect(splitSections([])).toEqual([]);
  });

  it('首頁進度卡挑清單裡第一張進行中的', () => {
    const endedNewest = task({ id: 'ended', state: 'done', createdAt: at(9) });
    const newer = task({ id: 'newer', state: 'triage', createdAt: at(3) });
    const older = task({ id: 'older', state: 'pool', createdAt: at(0) });
    expect(latestActive([endedNewest, newer, older])?.id).toBe('newer');
  });

  it('沒有進行中的回 null', () => {
    expect(latestActive([task({ state: 'cancelled' })])).toBeNull();
  });
});

describe('homeRequestsSection', () => {
  const at = (minute: number) => `2026-11-20T06:${String(minute).padStart(2, '0')}:00Z`;

  it('這次住宿一張都沒有時整塊不出現', () => {
    expect(homeRequestsSection([])).toEqual({ kind: 'none' });
  });

  it('有進行中的需求時是最新那張的進度卡，件數含已結束的', () => {
    const towel = task({ id: 'towel', state: 'pool', createdAt: at(0) });
    const water = task({ id: 'water', state: 'pool', createdAt: at(5) });
    const slippers = task({ id: 'slippers', state: 'done', createdAt: at(9) });
    expect(homeRequestsSection([slippers, water, towel])).toEqual({ kind: 'card', task: water, count: 3 });
  });

  it('全部結束時是「目前沒有進行中的需求」，件數含已取消的', () => {
    const tasks = [task({ id: 'a', state: 'done' }), task({ id: 'b', state: 'cancelled' })];
    expect(homeRequestsSection(tasks)).toEqual({ kind: 'idle', count: 2 });
  });
});
