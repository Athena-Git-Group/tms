import i18next, { type TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import type { DeskTaskDetail } from '../api/types';
import { zhTW } from '../i18n/locales/zh-TW';
import { displayString } from './display-text';
import { statusCard, statusTag, type StatusCardTone, type StatusTagTone } from './status-card';

const zh = i18next.createInstance();
void zh.init({
  lng: 'zh-TW',
  resources: { 'zh-TW': { translation: zhTW } },
  initAsync: false,
  interpolation: { escapeValue: false },
});
const t = zh.t as unknown as TFunction;

const NOW = new Date('2026-11-17T06:40:00Z');
const TAIPEI = 480;

function taipei(clock: string): string {
  const [hours = 0, minutes = 0] = clock.split(':').map(Number);
  return new Date(Date.UTC(2026, 10, 17, hours - 8, minutes)).toISOString();
}

function task(overrides: Partial<DeskTaskDetail>): DeskTaskDetail {
  return {
    id: 't-1',
    displayNo: 'T-1001',
    state: 'pool',
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: '412',
    floor: 4,
    vip: false,
    title: '加一條浴巾',
    guestTyped: false,
    createdAt: taipei('14:36'),
    updatedAt: taipei('14:36'),
    serverTime: NOW.toISOString(),
    timeline: [],
    ...overrides,
  };
}

function tagOf(overrides: Partial<DeskTaskDetail>): { text: string; tone: StatusTagTone } {
  const tag = statusTag(task(overrides));
  return { text: displayString(tag.text, t), tone: tag.tone };
}

function cardOf(
  overrides: Partial<DeskTaskDetail>,
): { title: string; description: string | null; tone: StatusCardTone } | null {
  const card = statusCard(task(overrides), NOW, TAIPEI);
  if (!card) return null;
  return {
    title: displayString(card.title, t),
    description: card.description ? displayString(card.description, t) : null,
    tone: card.tone,
  };
}

describe('statusTag：十四種標籤', () => {
  it('待派單，黃', () => {
    expect(tagOf({ state: 'triage', dept: 'frontdesk', type: 'other' })).toEqual({ text: '待派單', tone: 'yellow' });
  });

  it('待認領，品牌淺底', () => {
    expect(tagOf({ state: 'pool' })).toEqual({ text: '待認領', tone: 'brand-soft' });
  });

  it('已接，灰', () => {
    expect(tagOf({ state: 'accepted' })).toEqual({ text: '已接', tone: 'gray' });
  });

  it('進行中，品牌色實底', () => {
    expect(tagOf({ state: 'doing' })).toEqual({ text: '進行中', tone: 'brand' });
  });

  it('備品或其他類完成是「已完成」，淡綠', () => {
    expect(tagOf({ state: 'done', type: 'amenity' })).toEqual({ text: '已完成', tone: 'green' });
    expect(tagOf({ state: 'done', type: 'other' })).toEqual({ text: '已完成', tone: 'green' });
  });

  it('打掃單完成是「待查房」，淡綠', () => {
    expect(tagOf({ state: 'done', type: 'cleaning' })).toEqual({ text: '待查房', tone: 'green' });
  });

  it('修繕單完成是「已完修」，淡綠', () => {
    expect(tagOf({ state: 'done', type: 'repair', dept: 'engineering' })).toEqual({ text: '已完修', tone: 'green' });
  });

  it('查房通過，淡綠', () => {
    expect(tagOf({ state: 'inspected', type: 'cleaning', inspection: 'passed' })).toEqual({
      text: '查房通過',
      tone: 'green',
    });
  });

  it('免查房結案的打掃單寫「免查房」，不寫「查房通過」，淡綠', () => {
    expect(tagOf({ state: 'inspected', type: 'cleaning', inspection: 'waived' })).toEqual({
      text: '免查房',
      tone: 'green',
    });
  });

  it('已結案，淡綠', () => {
    expect(tagOf({ state: 'closed', type: 'repair', dept: 'engineering' })).toEqual({ text: '已結案', tone: 'green' });
  });

  it('櫃檯當場回答的單（完成且記有回答）寫「已回答」，淡綠', () => {
    expect(tagOf({ state: 'done', answered: true, dept: 'frontdesk', type: 'other' })).toEqual({
      text: '已回答',
      tone: 'green',
    });
  });

  it('中斷，淡紅', () => {
    expect(tagOf({ state: 'interrupted' })).toEqual({ text: '中斷', tone: 'red' });
  });

  it('延後中，灰', () => {
    expect(tagOf({ state: 'deferred' })).toEqual({ text: '延後中', tone: 'gray' });
  });

  it('已取消，灰', () => {
    expect(tagOf({ state: 'cancelled' })).toEqual({ text: '已取消', tone: 'gray' });
  });
});

describe('statusCard：每一種訊息卡', () => {
  it('306 櫃檯已當場回答：回覆原文與回答的人，淡綠', () => {
    expect(
      cardOf({
        state: 'done',
        answered: true,
        dept: 'frontdesk',
        type: 'other',
        answer: '退房時間是中午 12:00。',
        answeredByName: '林佩君',
      }),
    ).toEqual({ title: '櫃檯已當場回答', description: '「退房時間是中午 12:00。」— 林佩君', tone: 'green' });
  });

  it('412 待認領：哪個部門在等、已經等了幾分，品牌淺底', () => {
    expect(cardOf({ state: 'pool', waitingSince: taipei('14:36') })).toEqual({
      title: '房務待認領中，等同仁接單',
      description: '已等 4 分。同仁認領後這裡會即時更新。',
      tone: 'brand-soft',
    });
  });

  describe('已接的四種說明，白底、沒有逾時', () => {
    it('208 班前排房指派', () => {
      const card = cardOf({
        state: 'accepted',
        type: 'cleaning',
        assigneeName: '小美',
        accepted: { via: 'plan_assigned', at: taipei('13:10') },
      });
      expect(card).toEqual({ title: '小美 已接，還沒開始', description: '班前排房 指派。', tone: 'white' });
    });

    it('412 自己認領', () => {
      const card = cardOf({
        state: 'accepted',
        assigneeName: '小林',
        accepted: { via: 'self_claimed', at: taipei('10:12') },
      });
      expect(card).toEqual({ title: '小林 已接，還沒開始', description: '認領於 10:12。', tone: 'white' });
    });

    it('409 主管從待認領指派', () => {
      const card = cardOf({
        state: 'accepted',
        assigneeName: '阿強',
        accepted: { via: 'manager_assigned', at: taipei('10:40'), byName: '張主管' },
      });
      expect(card).toEqual({
        title: '阿強 已接，還沒開始',
        description: '張主管 10:40 從待認領指派 · 從現在開始計時。',
        tone: 'white',
      });
    });

    it('410 主管轉派', () => {
      const card = cardOf({
        state: 'accepted',
        assigneeName: '小陳',
        accepted: { via: 'reassigned', at: taipei('11:05'), byName: '張主管', previousAssigneeName: '阿美' },
      });
      expect(card).toEqual({
        title: '小陳 已接，還沒開始',
        description: '張主管 11:05 轉派（原：阿美） · 從現在重新計時。',
        tone: 'white',
      });
      expect(card?.description).not.toContain('逾時');
    });
  });

  it('510 進行中：誰在處理、已經幾分、幾點開始，淡綠', () => {
    expect(
      cardOf({ state: 'doing', type: 'cleaning', assigneeName: '小林', startedAt: taipei('14:10') }),
    ).toEqual({
      title: '小林 處理中 · 已 30 分',
      description: '開始於 14:10。住客來問時可以說「同仁正在處理」。',
      tone: 'green',
    });
  });

  describe('已完成的三種，淡綠', () => {
    it('406 備品單完成：完成的時刻', () => {
      expect(cardOf({ state: 'done', type: 'amenity', assigneeName: '小美', doneAt: taipei('14:20') })).toEqual({
        title: '已完成',
        description: '完成於 14:20，住客會收到通知並可以評分。',
        tone: 'green',
      });
    });

    it('301 打掃單等主管查房', () => {
      expect(cardOf({ state: 'done', type: 'cleaning', assigneeName: '小美', doneAt: taipei('13:50') })).toEqual({
        title: '已完成，等主管查房',
        description: '查房通過後房況才會回寫 PMS 變成可售。',
        tone: 'green',
      });
    });

    it('207 修繕單已完修：只有標題', () => {
      expect(
        cardOf({ state: 'done', type: 'repair', dept: 'engineering', assigneeName: '阿強', doneAt: taipei('13:30') }),
      ).toEqual({ title: '已完修 · 等主管驗收', description: null, tone: 'green' });
    });
  });

  it('303 查房通過：可售、已回寫 PMS，淡綠', () => {
    expect(cardOf({ state: 'inspected', type: 'cleaning', inspection: 'passed' })).toEqual({
      title: '查房通過，可售',
      description: '房況已回寫 PMS。',
      tone: 'green',
    });
  });

  it('315 免查房：不寫「可售」，淡綠', () => {
    const card = cardOf({ state: 'inspected', type: 'cleaning', inspection: 'waived' });
    expect(card).toEqual({
      title: '已完成 · 免查房',
      description: '這類打掃不用查房，完成時直接結案。',
      tone: 'green',
    });
    expect(`${card?.title}${card?.description}`).not.toContain('可售');
  });

  describe('已結案：只有標題，淡綠', () => {
    it('405 主管驗收通過', () => {
      expect(
        cardOf({ state: 'closed', type: 'repair', dept: 'engineering', repair: { resolutionType: 'fixed', verifiedByName: '張主管' } }),
      ).toEqual({ title: '已結案 · 張主管驗收', description: null, tone: 'green' });
    });

    it('407 不需處理', () => {
      expect(
        cardOf({ state: 'closed', type: 'repair', dept: 'engineering', repair: { resolutionType: 'no_action_needed' } }),
      ).toEqual({ title: '已結案 · 不需處理', description: null, tone: 'green' });
    });

    it('其他（規格沒寫：沒有驗收者也不是不需處理）只寫「已結案」', () => {
      expect(cardOf({ state: 'closed', type: 'amenity' })).toEqual({ title: '已結案', description: null, tone: 'green' });
    });
  });

  describe('中斷，淡紅', () => {
    it('309 同仁請假：做到哪', () => {
      expect(
        cardOf({
          state: 'interrupted',
          type: 'cleaning',
          assigneeName: '小美',
          interruption: { bySelf: false, handoverNote: '浴室洗好了，床還沒鋪。' },
        }),
      ).toEqual({ title: '小美 請假，任務中斷', description: '做到哪：浴室洗好了，床還沒鋪。', tone: 'red' });
    });

    it('請假沒有交代時寫「—」', () => {
      expect(
        cardOf({ state: 'interrupted', assigneeName: '小美', interruption: { bySelf: false, handoverNote: null } })
          ?.description,
      ).toBe('做到哪：—');
    });

    it('311 本人暫停：誰暫停與他填的原因', () => {
      expect(
        cardOf({
          state: 'interrupted',
          type: 'cleaning',
          assigneeName: '小美',
          interruption: { bySelf: true, reason: '住客還在房間裡' },
        }),
      ).toEqual({ title: '小美 暫停處理，任務中斷', description: '原因：住客還在房間裡', tone: 'red' });
    });
  });

  describe('延後中，白底', () => {
    it('502 主管延後', () => {
      expect(
        cardOf({
          state: 'deferred',
          type: 'cleaning',
          assigneeName: '小林',
          deferral: { bySelf: false, byName: '張主管' },
        }),
      ).toEqual({ title: '主管延後', description: '張主管 延後', tone: 'white' });
    });

    it('503 本人延後：誰延後與他填的原因', () => {
      expect(
        cardOf({
          state: 'deferred',
          type: 'cleaning',
          assigneeName: '小林',
          deferral: { bySelf: true, byName: '小林', reason: '住客下午才退房' },
        }),
      ).toEqual({ title: '小林 延後處理', description: '原因：住客下午才退房', tone: 'white' });
    });
  });

  describe('已取消的四種，白底', () => {
    it('210 主管取消：誰、幾點', () => {
      expect(
        cardOf({
          state: 'cancelled',
          endedAt: taipei('13:50'),
          cancellation: { reason: 'manager_void', byName: '張主管' },
        }),
      ).toEqual({ title: '已取消', description: '張主管 13:50 取消。不計入同仁的完成數。', tone: 'white' });
    });

    it('211 住客自己收回', () => {
      expect(
        cardOf({ state: 'cancelled', endedAt: taipei('13:20'), cancellation: { reason: 'guest_withdrew' } }),
      ).toEqual({ title: '已取消', description: '住客在還沒有人認領前自己收回，不計入任何統計。', tone: 'white' });
    });

    it('212 排房被取代', () => {
      expect(
        cardOf({
          state: 'cancelled',
          type: 'cleaning',
          endedAt: taipei('09:00'),
          cancellation: { reason: 'plan_superseded' },
        }),
      ).toEqual({ title: '已取消', description: '排房調整後不再需要這張單。不計入同仁的完成數。', tone: 'white' });
    });

    it('213 同仁撤回報修：報修人、幾點', () => {
      expect(
        cardOf({
          state: 'cancelled',
          type: 'repair',
          dept: 'engineering',
          endedAt: taipei('11:20'),
          cancellation: { reason: 'staff_withdrew', byName: '小美' },
        }),
      ).toEqual({ title: '已取消', description: '小美 11:20 撤回報修。不計入同仁的完成數。', tone: 'white' });
    });

    it('同仁撤回報修沒記撤回者時，用開單的人', () => {
      expect(
        cardOf({
          state: 'cancelled',
          type: 'repair',
          createdByName: '小美',
          endedAt: taipei('11:20'),
          cancellation: { reason: 'staff_withdrew', byName: null },
        })?.description,
      ).toBe('小美 11:20 撤回報修。不計入同仁的完成數。');
    });
  });

  it('305 待派單沒有訊息卡', () => {
    expect(cardOf({ state: 'triage', dept: 'frontdesk', type: 'other' })).toBeNull();
  });
});
