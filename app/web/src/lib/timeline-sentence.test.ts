import i18next, { type TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import type { DeskTaskDetail, DeskTimelineEntry } from '../api/types';
import { zhTW } from '../i18n/locales/zh-TW';
import { displayString } from './display-text';
import { type TimelineDotTone, timelineMeta, timelineSentence } from './timeline-sentence';

const zh = i18next.createInstance();
void zh.init({
  lng: 'zh-TW',
  resources: { 'zh-TW': { translation: zhTW } },
  initAsync: false,
  interpolation: { escapeValue: false },
});
const t = zh.t as unknown as TFunction;

const NOW = new Date('2026-10-26T06:40:00Z');
const TAIPEI = 480;

function task(overrides: Partial<DeskTaskDetail> = {}): DeskTaskDetail {
  return {
    id: 't-1',
    displayNo: 'T-1001',
    state: 'pool',
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '房間有怪聲',
    guestTyped: true,
    guestText: { original: '房間有怪聲' },
    createdAt: '2026-10-26T06:02:00Z',
    updatedAt: '2026-10-26T06:02:00Z',
    serverTime: NOW.toISOString(),
    timeline: [],
    ...overrides,
  };
}

function entry(overrides: Partial<DeskTimelineEntry>): DeskTimelineEntry {
  return {
    seq: 1,
    at: '2026-10-26T06:05:00Z',
    type: 'triaged',
    actor: { kind: 'staff', role: 'frontdesk', name: '林佩君' },
    ...overrides,
  };
}

const GUEST = { kind: 'guest', name: '陳怡安' } as const;
const SYSTEM = { kind: 'system' } as const;
const staff = (name: string, role: 'frontdesk' | 'manager' | 'housekeeping' = 'housekeeping') => ({ kind: 'staff', role, name }) as const;

function rowOf(
  event: DeskTimelineEntry,
  of: DeskTaskDetail = task(),
): { sentence: string; actor: string; dot: TimelineDotTone } | null {
  const sentence = timelineSentence(event, of);
  if (!sentence) return null;
  return { sentence: displayString(sentence.text, t), actor: displayString(sentence.actor, t), dot: sentence.dot };
}

describe('timelineSentence：已定案的句子', () => {
  it('住客打字建立：寫住客原文，不寫翻譯（:18）', () => {
    const created = entry({
      type: 'created',
      actor: GUEST,
      detail: { dept: 'frontdesk', initialState: 'triage', guestTyped: true },
    });
    const of = task({ title: 'Strange noise in room', guestText: { original: '房間有怪聲', translated: 'Strange noise in room' } });
    expect(rowOf(created, of)).toEqual({ sentence: '掃碼送出文字需求「房間有怪聲」', actor: '住客（陳怡安）', dot: 'brand' });
  });

  it('住客掃碼勾選（:79）', () => {
    const created = entry({
      type: 'created',
      actor: GUEST,
      detail: { dept: 'housekeeping', initialState: 'pool', guestTyped: false },
    });
    expect(rowOf(created, task({ roomNo: '412', title: '加一條浴巾', guestTyped: false, guestText: null }))).toEqual({
      sentence: '掃碼勾選「加一條浴巾」，依選單進房務待認領',
      actor: '住客（陳怡安）',
      dot: 'brand',
    });
  });

  it('櫃檯代開（:80）', () => {
    const created = entry({ type: 'created', actor: staff('何志偉', 'frontdesk'), detail: { dept: 'engineering', initialState: 'pool' } });
    const of = task({ roomNo: '207', title: '冷氣不冷', type: 'repair', source: 'desk', dept: 'engineering', guestTyped: false, guestText: null });
    expect(rowOf(created, of)).toEqual({ sentence: '櫃檯代開「冷氣不冷」，進工務待認領', actor: '何志偉', dot: 'purple' });
  });

  it.each([
    ['button', 'engineering', '派給工務，進工務待認領'],
    ['button', 'housekeeping', '派給房務，進房務待認領'],
    ['drag', 'engineering', '派給工務（拖曳），進工務待認領'],
    ['drag', 'housekeeping', '派給房務（拖曳），進房務待認領'],
  ] as const)('以 %s 派給 %s（:35–:38）', (via, dept, sentence) => {
    const triaged = entry({ type: 'triaged', actor: staff('林佩君', 'frontdesk'), detail: { dept, via } });
    expect(rowOf(triaged, task({ dept }))).toEqual({ sentence, actor: '林佩君', dot: 'purple' });
  });

  it('沒帶派單方式的派單當成按鈕（:19）', () => {
    const triaged = entry({ type: 'triaged', detail: { dept: 'housekeeping' } });
    expect(rowOf(triaged)?.sentence).toBe('派給房務，進房務待認領');
  });

  it('當場回答帶回覆全文（:48）', () => {
    const answered = entry({
      type: 'answered',
      actor: staff('林佩君', 'frontdesk'),
      detail: { answer: '早餐 07:00–10:00，在 2 樓自助餐廳，憑房卡入場。' },
    });
    expect(rowOf(answered, task({ roomNo: '306', title: '早餐幾點', dept: 'frontdesk' }))).toEqual({
      sentence: '櫃檯當場回答並結案：「早餐 07:00–10:00，在 2 樓自助餐廳，憑房卡入場。」',
      actor: '林佩君',
      dot: 'purple',
    });
  });

  it('住客收回（:58）', () => {
    const cancelled = entry({ type: 'cancelled', actor: GUEST, detail: { cancelReason: 'guest_withdrew' } });
    expect(rowOf(cancelled, task({ roomNo: '211', state: 'cancelled' }))).toEqual({
      sentence: '住客自己收回需求（還沒有人認領）',
      actor: '住客（陳怡安）',
      dot: 'orange',
    });
  });

  it('空房的住客沒有名字時操作者只寫「住客」', () => {
    const cancelled = entry({ type: 'cancelled', actor: { kind: 'guest', name: null }, detail: { cancelReason: 'guest_withdrew' } });
    expect(rowOf(cancelled)?.actor).toBe('住客');
  });

  it('班前排房：操作者寫系統排房（:81）', () => {
    const assigned = entry({ type: 'assigned', actor: SYSTEM, detail: { assigneeName: '小美' } });
    expect(rowOf(assigned, task({ roomNo: '208', type: 'cleaning', source: 'system', title: '退房打掃' }))).toEqual({
      sentence: '班前排房，指派給 小美',
      actor: '系統排房',
      dot: 'brand',
    });
  });

  it('同仁認領（:20）', () => {
    const claimed = entry({ type: 'claimed', actor: staff('小美'), detail: { assigneeName: '小美' } });
    expect(rowOf(claimed)).toEqual({ sentence: '小美 認領', actor: '小美', dot: 'brand' });
  });

  it('認領事件沒帶負責人時，句子用操作者的名字', () => {
    const claimed = entry({ type: 'claimed', actor: staff('小美') });
    expect(rowOf(claimed)?.sentence).toBe('小美 認領');
  });

  it('開始處理（:21）', () => {
    expect(rowOf(entry({ type: 'started', actor: staff('小美') }))).toEqual({ sentence: '開始處理', actor: '小美', dot: 'brand' });
  });

  it('同仁完成備品單（:82）', () => {
    const completed = entry({ type: 'completed', actor: staff('小美') });
    expect(rowOf(completed, task({ roomNo: '406', type: 'amenity', state: 'done' }))).toEqual({
      sentence: '回報完成',
      actor: '小美',
      dot: 'green',
    });
  });

  it('同仁完成打掃單，等主管查房（:83）', () => {
    const completed = entry({ type: 'completed', actor: staff('小美') });
    const of = task({ roomNo: '301', type: 'cleaning', source: 'system', state: 'done', inspection: null });
    expect(rowOf(completed, of)).toEqual({ sentence: '回報完成，等主管查房', actor: '小美', dot: 'green' });
  });

  it('免查房的打掃單完成時不寫等主管查房（feature 沒寫，照現行行為）', () => {
    const completed = entry({ type: 'completed', actor: staff('小美') });
    expect(rowOf(completed, task({ type: 'cleaning', inspection: 'waived' }))?.sentence).toBe('回報完成');
  });

  it('主管查房通過（:84）', () => {
    const passed = entry({ type: 'inspection_passed', actor: staff('張主管', 'manager') });
    expect(rowOf(passed, task({ roomNo: '303', type: 'cleaning', inspection: 'passed' }))).toEqual({
      sentence: '查房全部通過，房況回寫 PMS',
      actor: '張主管',
      dot: 'green',
    });
  });

  it('同仁請假造成中斷：寫目前負責人，操作者是系統（:85）', () => {
    const interrupted = entry({ type: 'interrupted', actor: SYSTEM, detail: { bySelf: false } });
    expect(rowOf(interrupted, task({ roomNo: '309', type: 'cleaning', assigneeName: '小美' }))).toEqual({
      sentence: '小美 請假，進行中的任務標記中斷',
      actor: '系統',
      dot: 'orange',
    });
  });

  it('主管延後只寫「延後」，不寫原因（:86）', () => {
    const deferred = entry({ type: 'deferred', actor: staff('張主管', 'manager'), detail: { bySelf: false, reason: '住客在房' } });
    expect(rowOf(deferred, task({ roomNo: '502', type: 'cleaning' }))).toEqual({ sentence: '延後', actor: '張主管', dot: 'orange' });
  });
});

describe('timelineSentence：ui-plan 暫定的句子（:128–:134）', () => {
  it('同仁報修', () => {
    const created = entry({ type: 'created', actor: staff('小美'), detail: { dept: 'engineering', initialState: 'pool' } });
    const of = task({ roomNo: '207', title: '冷氣不冷', type: 'repair', source: 'staff', dept: 'engineering', guestTyped: false });
    expect(rowOf(created, of)).toEqual({ sentence: '報修「冷氣不冷」，進工務待認領', actor: '小美', dot: 'brand' });
  });

  it('主管轉派', () => {
    const reassigned = entry({
      type: 'reassigned',
      actor: staff('張主管', 'manager'),
      detail: { assigneeName: '小陳', previousAssigneeName: '阿美' },
    });
    expect(rowOf(reassigned, task({ roomNo: '410' }))).toEqual({ sentence: '轉派給 小陳（原：阿美）', actor: '張主管', dot: 'orange' });
  });

  it('工務單驗收通過', () => {
    const verified = entry({ type: 'repair_verified', actor: staff('張主管', 'manager') });
    expect(rowOf(verified, task({ roomNo: '405', type: 'repair' }))).toEqual({ sentence: '驗收通過，結案', actor: '張主管', dot: 'green' });
  });

  it('同仁自己暫停', () => {
    const paused = entry({ type: 'interrupted', actor: staff('小美'), detail: { bySelf: true, reason: '住客還在房間裡' } });
    expect(rowOf(paused, task({ roomNo: '311' }))).toEqual({ sentence: '暫停處理（住客還在房間裡）', actor: '小美', dot: 'orange' });
  });

  it.each([
    ['manager_void', staff('張主管', 'manager'), '取消這張單', '張主管'],
    ['plan_superseded', SYSTEM, '排房調整，這張單取消', '系統'],
    ['staff_withdrew', staff('小美'), '撤回報修', '小美'],
  ] as const)('取消原因 %s', (cancelReason, actor, sentence, actorName) => {
    const cancelled = entry({ type: 'cancelled', actor, detail: { cancelReason } });
    expect(rowOf(cancelled)).toEqual({ sentence, actor: actorName, dot: 'orange' });
  });
});

describe('timelineSentence：feature 沒寫句子的事件不顯示', () => {
  it.each([
    ['rated', entry({ type: 'rated', actor: GUEST, detail: { rating: 5 } })],
    ['resumed', entry({ type: 'resumed', actor: staff('小美') })],
    ['returned_to_pool', entry({ type: 'returned_to_pool', actor: SYSTEM })],
    ['inspection_failed', entry({ type: 'inspection_failed', actor: staff('張主管', 'manager') })],
    ['repair_verification_failed', entry({ type: 'repair_verification_failed', actor: staff('張主管', 'manager') })],
    ['plan_adjusted', entry({ type: 'plan_adjusted', actor: SYSTEM })],
    ['checkout_imported', entry({ type: 'checkout_imported', actor: SYSTEM })],
    ['sop_checked', entry({ type: 'sop_checked', actor: staff('小美') })],
    ['主管從待認領指派（非系統的 assigned）', entry({ type: 'assigned', actor: staff('張主管', 'manager'), detail: { assigneeName: '小美' } })],
    ['本人延後', entry({ type: 'deferred', actor: staff('小美'), detail: { bySelf: true, reason: '住客在房' } })],
    ['系統建立（班前排房的單）', entry({ type: 'created', actor: SYSTEM, detail: { dept: 'housekeeping', initialState: 'accepted' } })],
    ['沒有取消原因的 cancelled', entry({ type: 'cancelled', actor: staff('張主管', 'manager'), detail: {} })],
    ['沒見過的事件種類', entry({ type: 'something_new', actor: SYSTEM })],
  ])('%s', (_name, event) => {
    expect(timelineSentence(event, task({ source: 'system', type: 'cleaning' }))).toBeNull();
  });
});

describe('timelineMeta', () => {
  const actor = { key: 'detail.events.actors.guest', values: { name: '陳怡安' } } as const;
  const metaOf = (at: string, now: Date | undefined, offset = TAIPEI) =>
    displayString(timelineMeta(entry({ at }), actor, now, offset), t);

  it('今天的那一筆只寫時刻（:18）', () => {
    expect(metaOf('2026-10-26T06:02:00Z', NOW)).toBe('14:02 · 住客（陳怡安）');
  });

  it('不是今天的那一筆前面寫日期，月日不補零（:146）', () => {
    expect(metaOf('2026-10-23T03:00:00Z', NOW)).toBe('10/23 11:00 · 住客（陳怡安）');
    expect(metaOf('2026-03-05T01:07:00Z', new Date('2026-03-10T01:00:00Z'))).toBe('3/5 09:07 · 住客（陳怡安）');
  });

  it('跨日邊界照伺服器時區：台北 00:00 算今天，前一分鐘 23:59 算昨天', () => {
    expect(metaOf('2026-10-25T16:00:00Z', NOW)).toBe('00:00 · 住客（陳怡安）');
    expect(metaOf('2026-10-25T15:59:00Z', NOW)).toBe('10/25 23:59 · 住客（陳怡安）');
  });

  it('同一個時刻，伺服器時區不同，寫出的時刻與是不是今天跟著變', () => {
    expect(metaOf('2026-10-25T16:30:00Z', NOW, 480)).toBe('00:30 · 住客（陳怡安）');
    expect(metaOf('2026-10-25T16:30:00Z', NOW, 0)).toBe('10/25 16:30 · 住客（陳怡安）');
    expect(metaOf('2026-10-25T16:30:00Z', NOW, -600)).toBe('06:30 · 住客（陳怡安）');
  });

  it('後端時刻字串帶時區偏移時照實際時刻算', () => {
    expect(metaOf('2026-10-26T14:02:00+08:00', NOW)).toBe('14:02 · 住客（陳怡安）');
    expect(metaOf('2026-10-23T11:00:00+08:00', NOW)).toBe('10/23 11:00 · 住客（陳怡安）');
  });

  it('還沒校正好現在時當成今天，只寫時刻', () => {
    expect(metaOf('2026-10-23T03:00:00Z', undefined)).toBe('11:00 · 住客（陳怡安）');
  });
});
