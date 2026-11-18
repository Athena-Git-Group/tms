import type { TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';
import { conflictText } from './conflict-text';

/** 假的翻譯函式：回傳「鍵」加上參數，方便斷言用的是哪一句、帶了什麼值。 */
const t = ((key: string, options?: Record<string, string>) =>
  options ? `${key}|${Object.values(options).join('|')}` : key) as unknown as TFunction;

const ASSIGN_TAIL = 'dispatch.conflict.assignTail';

describe('conflictText', () => {
  it('被派走：標題與第一句帶派單的人、房號、部門名稱', () => {
    const context = { resolution: 'assigned', actorName: '何志偉', dept: 'engineering', roomNo: '305' };
    expect(conflictText(context, '306', 'assign', t)).toEqual({
      title: 'dispatch.conflict.assigned.title',
      body: `dispatch.conflict.body|dispatch.conflict.assigned.head|何志偉|305|board.card.depts.engineering|${ASSIGN_TAIL}`,
    });
  });

  it('被當場回答：帶回答的人與房號', () => {
    const result = conflictText({ resolution: 'answered', actorName: '林佩君', roomNo: '306' }, '305', 'assign', t);
    expect(result.title).toBe('dispatch.conflict.answered.title');
    expect(result.body).toBe(`dispatch.conflict.body|dispatch.conflict.answered.head|林佩君|306|${ASSIGN_TAIL}`);
  });

  it('住客收回：只帶房號（呼叫端給的房號）', () => {
    const result = conflictText({ resolution: 'guest_withdrew' }, '305', 'assign', t);
    expect(result.title).toBe('dispatch.conflict.guestWithdrew.title');
    expect(result.body).toBe(`dispatch.conflict.body|dispatch.conflict.guestWithdrew.head|305|${ASSIGN_TAIL}`);
  });

  it('其他或後端沒說是哪一種：只說已經不在待派單', () => {
    expect(conflictText({ resolution: 'other' }, '305', 'assign', t).title).toBe('dispatch.conflict.other.title');
    expect(conflictText({}, '305', 'assign', t).title).toBe('dispatch.conflict.other.title');
  });

  it('派走卻沒帶合法的部門：退成「其他」', () => {
    const noDept = conflictText({ resolution: 'assigned', actorName: '何志偉' }, '305', 'assign', t);
    const badDept = conflictText({ resolution: 'assigned', actorName: '何志偉', dept: 'frontdesk' }, '305', 'assign', t);
    expect(noDept.title).toBe('dispatch.conflict.other.title');
    expect(badDept.title).toBe('dispatch.conflict.other.title');
  });

  it('房號以後端回的為準，沒有或空字串時用呼叫端給的', () => {
    expect(conflictText({ resolution: 'answered', roomNo: '306' }, '305', 'assign', t).body).toContain('|306|');
    expect(conflictText({ resolution: 'answered', roomNo: '' }, '305', 'assign', t).body).toContain('|305|');
  });

  it('沒帶 actorName：照現行寫法，名字是空字串', () => {
    const result = conflictText({ resolution: 'answered', roomNo: '305' }, '305', 'assign', t);
    expect(result.body).toBe(`dispatch.conflict.body|dispatch.conflict.answered.head||305|${ASSIGN_TAIL}`);
  });

  it('當場回答：第一句相同，最後一句換成「不需要再回覆」', () => {
    const context = { resolution: 'assigned', actorName: '林佩君', dept: 'engineering', roomNo: '402' };
    const assign = conflictText(context, '402', 'assign', t);
    const answer = conflictText(context, '402', 'answer', t);
    expect(answer.title).toBe(assign.title);
    expect(answer.body).toBe(
      'dispatch.conflict.body|dispatch.conflict.assigned.head|林佩君|402|board.card.depts.engineering|dispatch.conflict.answerTail',
    );
  });
});
