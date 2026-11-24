import { describe, expect, it } from 'vitest';
import { ApiError, NetworkError } from '../../api/client';
import type { DeskRoom, DeskTask } from '../../api/types';
import {
  EMPTY_DRAFT,
  MENU_ITEMS,
  avatarOf,
  canSubmit,
  clampOtherText,
  createRequestBody,
  filterRoomInput,
  hasOtherText,
  isUnauthorized,
  keepIdempotencyKey,
  nextOtherText,
  isValidRoom,
  monthDay,
  roomFeedback,
  quantityOf,
  roomStatus,
  similarTaskLines,
  submitFailureKind,
  suggestRooms,
  summaryLists,
  ticketCount,
  withQuantity,
  type CreateRequestDraft,
  type MenuItem,
} from './create-request-logic';

function occupied(roomNo: string, name: string): DeskRoom {
  return {
    roomNo,
    occupied: true,
    guest: {
      name,
      vip: false,
      checkinDate: '2026-10-26',
      checkoutDate: '2026-10-27',
    },
  };
}

function vacant(roomNo: string): DeskRoom {
  return { roomNo, occupied: false, guest: null };
}

const ROOMS: DeskRoom[] = [
  occupied('310', '陳先生'),
  occupied('207', '黃小姐'),
  vacant('301'),
  occupied('305', '王大明 先生'),
  occupied('303', '高先生'),
  vacant('308'),
  occupied('306', '李小姐'),
  occupied('311', '劉小姐'),
  occupied('220', '吳先生'),
  occupied('412', 'Sato Yuki 様'),
];

describe('房號輸入過濾', () => {
  it('只留半形數字，字母與全形數字都濾掉', () => {
    expect(filterRoomInput('3a0５5')).toBe('305');
  });

  it('最多 4 碼', () => {
    expect(filterRoomInput('30512')).toBe('3051');
  });
});

describe('房號判斷', () => {
  it('房間清單裡有住客的房是住客房', () => {
    expect(roomStatus('305', ROOMS, false)).toEqual({
      kind: 'occupied',
      room: occupied('305', '王大明 先生'),
    });
  });

  it('清單裡沒有住客的房是空房，可以送出', () => {
    const status = roomStatus('308', ROOMS, false);
    expect(status).toEqual({ kind: 'vacant', roomNo: '308' });
    expect(isValidRoom(status)).toBe(true);
  });

  it('清單裡沒有的房號與空白都是無效', () => {
    expect(isValidRoom(roomStatus('999', ROOMS, false))).toBe(false);
    expect(isValidRoom(roomStatus('', ROOMS, false))).toBe(false);
  });

  it('清單還在讀時判斷不了，不能送出', () => {
    const status = roomStatus('305', undefined, false);
    expect(status).toEqual({ kind: 'unknown' });
    expect(isValidRoom(status)).toBe(false);
  });

  it('清單讀不到時有數字就當有效', () => {
    const status = roomStatus('999', undefined, true);
    expect(status).toEqual({ kind: 'unchecked', roomNo: '999' });
    expect(isValidRoom(status)).toBe(true);
  });
});

describe('建議房號', () => {
  it('房號空白時列房號最小的 6 間住客房', () => {
    expect(suggestRooms('', ROOMS).map((room) => room.roomNo)).toEqual(['207', '220', '303', '305', '306', '310']);
  });

  it('只列以打的字開頭的住客房，空房不列', () => {
    expect(suggestRooms('3', ROOMS).map((room) => room.roomNo)).toEqual(['303', '305', '306', '310', '311']);
    expect(suggestRooms('30', ROOMS).map((room) => room.roomNo)).toEqual(['303', '305', '306']);
  });

  it('沒有符合的房時是空的', () => {
    expect(suggestRooms('999', ROOMS)).toEqual([]);
  });
});

describe('住客卡', () => {
  it('日期寫成 M/D', () => {
    expect(monthDay('2026-10-06')).toBe('10/6');
  });

  it('頭像是住客名第一個字', () => {
    expect(avatarOf('王大明 先生')).toBe('王');
    expect(avatarOf('Sato Yuki 様')).toBe('S');
  });
});

describe('房號欄底下顯示什麼', () => {
  const ready = { rooms: ROOMS, loading: false, failed: false };

  it('住客房出現住客卡、空房出現空房提示', () => {
    expect(roomFeedback('305', roomStatus('305', ROOMS, false), ready).kind).toBe('guest');
    expect(roomFeedback('308', roomStatus('308', ROOMS, false), ready)).toEqual({ kind: 'vacant', roomNo: '308' });
  });

  it('房號無效時列建議，沒有符合的就什麼都不顯示', () => {
    const half = roomFeedback('30', roomStatus('30', ROOMS, false), ready);
    expect(half.kind === 'suggestions' && half.rooms.map((room) => room.roomNo)).toEqual(['303', '305', '306']);
    expect(roomFeedback('999', roomStatus('999', ROOMS, false), ready)).toEqual({ kind: 'none' });
  });

  it('清單讀取中是骨架，讀不到時是讀取失敗', () => {
    expect(
      roomFeedback('', roomStatus('', undefined, false), {
        rooms: undefined,
        loading: true,
        failed: false,
      }),
    ).toEqual({
      kind: 'loading',
    });
    expect(
      roomFeedback('3', roomStatus('3', undefined, true), {
        rooms: undefined,
        loading: false,
        failed: true,
      }),
    ).toEqual({
      kind: 'listFailed',
    });
  });
});

describe('張數、送出鈕與摘要', () => {
  const zhWords = {
    itemName: (item: MenuItem) => item.key,
    other: '其他需求',
    listSeparator: '、',
  };
  const item = (key: string) => MENU_ITEMS.find((candidate) => candidate.key === key)!;
  const withItems = (entries: [string, number][]): CreateRequestDraft =>
    entries.reduce((draft, [key, quantity]) => withQuantity(draft, item(key), quantity), {
      ...EMPTY_DRAFT,
      roomNo: '305',
    });

  it('一個品項不論幾份都只算一張', () => {
    expect(
      ticketCount(
        withItems([
          ['毛巾', 2],
          ['礦泉水', 1],
        ]),
      ),
    ).toBe(2);
  });

  it('數量停在 0 到上限之間', () => {
    expect(quantityOf(withItems([['嬰兒床', 3]]), '嬰兒床')).toBe(2);
    expect(quantityOf(withItems([['毛巾', 12]]), '毛巾')).toBe(9);
    expect(quantityOf(withItems([['冷氣', 2]]), '冷氣')).toBe(1);
    expect(withItems([['毛巾', 0]]).quantities).toEqual({});
  });

  it('房號有效而且至少一張才能送出', () => {
    const rooms = [occupied('305', '王大明 先生')];
    expect(canSubmit(withItems([['毛巾', 1]]), roomStatus('305', rooms, false))).toBe(true);
    expect(canSubmit(withItems([]), roomStatus('305', rooms, false))).toBe(false);
    expect(canSubmit(withItems([['毛巾', 1]]), roomStatus('30', rooms, false))).toBe(false);
  });

  it('房務品項帶份數、工務品項只寫品名，依品項順序分兩段', () => {
    expect(
      summaryLists(
        withItems([
          ['冷氣', 1],
          ['毛巾', 2],
          ['熱水', 1],
        ]),
        zhWords,
      ),
    ).toEqual({
      housekeeping: '毛巾 ×2',
      engineering: '冷氣、熱水',
    });
    expect(summaryLists(withItems([['冰塊', 2]]), zhWords)).toEqual({
      housekeeping: '冰塊 ×2',
      engineering: null,
    });
  });

  it('什麼都沒選時沒有摘要', () => {
    expect(summaryLists(withItems([]), zhWords)).toBeNull();
  });
});

describe('其他需求', () => {
  const zhWords = {
    itemName: (item: MenuItem) => item.key,
    other: '其他需求',
    listSeparator: '、',
  };
  const towel = MENU_ITEMS[0]!;
  const airConditioner = MENU_ITEMS.find((item) => item.key === '冷氣')!;

  it('只打空白不算有填，也不算一張', () => {
    const draft = { ...EMPTY_DRAFT, roomNo: '305', otherText: '   ' };
    expect(hasOtherText(draft)).toBe(false);
    expect(ticketCount(draft)).toBe(0);
  });

  it('其他需求排在所選部門那一段的最後', () => {
    const base = withQuantity(withQuantity({ ...EMPTY_DRAFT, roomNo: '305' }, towel, 2), airConditioner, 1);
    expect(summaryLists({ ...base, otherText: '多給兩個衣架' }, zhWords)).toEqual({
      housekeeping: '毛巾 ×2、其他需求',
      engineering: '冷氣',
    });
    expect(summaryLists({ ...base, otherText: '浴室門卡住', otherDept: 'engineering' }, zhWords)).toEqual({
      housekeeping: '毛巾 ×2',
      engineering: '冷氣、其他需求',
    });
  });

  it('最多 60 個字，中文一字算一字', () => {
    expect(Array.from(clampOtherText('字'.repeat(70)))).toHaveLength(60);
    expect(clampOtherText('住客明早 6 點要叫車')).toBe('住客明早 6 點要叫車');
  });

  it('已經 60 個字時，多打或貼上的字都進不去', () => {
    const full = '天'.repeat(60);
    expect(nextOtherText(full, `天天${'地'.repeat(10)}${'天'.repeat(58)}`)).toBe(full);
    expect(nextOtherText('天'.repeat(59), '天'.repeat(62))).toBe('天'.repeat(60));
  });

  it('送出的是去掉前後空白的字，沒有字時不帶其他需求', () => {
    expect(
      createRequestBody({
        ...EMPTY_DRAFT,
        roomNo: '305',
        otherText: ' 浴室門卡住 ',
      }).other,
    ).toEqual({
      text: '浴室門卡住',
      dept: 'housekeeping',
    });
    expect(createRequestBody(withQuantity({ ...EMPTY_DRAFT, roomNo: '305' }, towel, 2))).toEqual({
      roomNo: '305',
      items: [{ menuKey: '毛巾', quantity: 2 }],
      other: null,
    });
  });
});

describe('類似單提醒', () => {
  const now = new Date('2026-10-26T10:30:00+08:00');
  const task = (overrides: Partial<DeskTask>): DeskTask => ({
    id: overrides.title ?? 'id',
    seq: 1,
    displayNo: 'T-1',
    state: 'pool',
    answered: false,
    dept: 'housekeeping',
    type: 'amenity',
    source: 'guest',
    roomNo: '305',
    floor: 3,
    vip: false,
    title: '毛巾 ×2',
    guestTyped: false,
    createdAt: '2026-10-26T10:18:00+08:00',
    updatedAt: '2026-10-26T10:18:00+08:00',
    ...overrides,
  });
  const towel = MENU_ITEMS[0]!;
  const draftWithTowel = withQuantity({ ...EMPTY_DRAFT, roomNo: '305' }, towel, 1);

  it('同房、還沒結束、對應品項相同的單才算', () => {
    const tasks = [
      task({ title: '毛巾 ×2', menuKey: '毛巾' }),
      task({ title: '已完成', menuKey: '毛巾', state: 'done' }),
      task({ title: '中斷', menuKey: '毛巾', state: 'interrupted' }),
      task({ title: '別間房', menuKey: '毛巾', roomNo: '412' }),
      task({ title: '浴巾 ×1', menuKey: '浴巾' }),
    ];
    expect(similarTaskLines(draftWithTowel, tasks, now)).toEqual([
      {
        taskId: '毛巾 ×2',
        title: '毛巾 ×2',
        state: 'pool',
        assigneeName: null,
        minutes: 12,
      },
    ]);
  });

  it('有填其他需求時列住客打字的單，標題用住客的原話', () => {
    const typed = task({
      title: '房間有怪聲',
      state: 'triage',
      guestTyped: true,
      guestText: { original: '房間有怪聲' },
    });
    expect(similarTaskLines({ ...EMPTY_DRAFT, roomNo: '305' }, [typed], now)).toEqual([]);
    expect(similarTaskLines({ ...EMPTY_DRAFT, roomNo: '305', otherText: '浴室門卡住' }, [typed], now)[0]?.title).toBe(
      '房間有怪聲',
    );
  });

  it('依建立時間由新到舊，未滿 1 分鐘是 0 分，時鐘落後也不會是負數', () => {
    const lines = similarTaskLines(
      draftWithTowel,
      [
        task({
          title: '舊',
          menuKey: '毛巾',
          createdAt: '2026-10-26T10:18:00+08:00',
        }),
        task({
          title: '新',
          menuKey: '毛巾',
          createdAt: '2026-10-26T10:31:00+08:00',
        }),
      ],
      now,
    );
    expect(lines.map((line) => [line.title, line.minutes])).toEqual([
      ['新', 0],
      ['舊', 12],
    ]);
  });
});

describe('送出失敗', () => {
  const apiError = (status: number, code: string) => new ApiError(status, { code, message: '' });

  it('沒有回應、逾時或伺服器錯誤時保留冪等鍵，內容沒改再送拿同一次的結果', () => {
    expect(keepIdempotencyKey(new NetworkError(new TypeError('Failed to fetch')))).toBe(true);
    expect(keepIdempotencyKey(new DOMException('aborted', 'AbortError'))).toBe(true);
    expect(keepIdempotencyKey(apiError(500, 'internal_error'))).toBe(true);
  });

  it('收到 4xx 就丟掉冪等鍵', () => {
    expect(keepIdempotencyKey(apiError(404, 'not_found'))).toBe(false);
    expect(keepIdempotencyKey(apiError(422, 'validation_failed'))).toBe(false);
  });

  it('失敗回應對到四種提示', () => {
    expect(submitFailureKind(apiError(404, 'not_found'))).toBe('room_not_found');
    expect(submitFailureKind(apiError(403, 'forbidden'))).toBe('forbidden');
    expect(submitFailureKind(apiError(422, 'validation_failed'))).toBe('invalid');
    expect(submitFailureKind(apiError(409, 'some_business_error'))).toBe('invalid');
    expect(submitFailureKind(apiError(503, 'unavailable'))).toBe('not_sent');
    expect(submitFailureKind(new NetworkError(new TypeError('Load failed')))).toBe('not_sent');
    expect(submitFailureKind(new DOMException('aborted', 'AbortError'))).toBe('not_sent');
  });

  it('登入逾時不跳提示', () => {
    expect(isUnauthorized(apiError(401, 'unauthorized'))).toBe(true);
    expect(isUnauthorized(apiError(403, 'forbidden'))).toBe(false);
  });
});
