import { describe, expect, test } from 'vitest';
import {
  BOARD_VIEW_STATE,
  LOGIN_PAGE_LANGUAGE_STATE,
  SESSION_STATE,
  STAFF_LANGUAGE_STATE,
  STATE_PERSISTENCE,
  TAB_STORED_STATES,
} from './persistence';

describe('persistence', () => {
  test('鍵的字串和儲存種類維持原樣，使用者已經存著的內容才讀得回來', () => {
    expect(SESSION_STATE).toEqual({ kind: 'session', key: 'tms.frontdesk.session' });
    expect(BOARD_VIEW_STATE).toEqual({ kind: 'session', key: 'tms.frontdesk.board-view' });
    expect(LOGIN_PAGE_LANGUAGE_STATE).toEqual({
      kind: 'local',
      key: 'tms.frontdesk.language.login-page',
    });
    expect(STAFF_LANGUAGE_STATE).toEqual({
      kind: 'local',
      keyPrefix: 'tms.frontdesk.language.staff.',
    });
  });

  test('跟著分頁的鍵清單和說明表裡標成跟著分頁的一致，而且全都存在分頁的儲存空間', () => {
    const tabStatesInTable = new Set(
      STATE_PERSISTENCE.filter((rule) => rule.scope === 'tab').map((rule) => rule.stored),
    );
    expect(new Set(TAB_STORED_STATES)).toEqual(tabStatesInTable);
    for (const state of TAB_STORED_STATES) {
      expect(state.kind).toBe('session');
    }
  });

  test('跟著分頁的登出要清，跟著裝置的登出不清，存到瀏覽器的都標了鍵', () => {
    for (const rule of STATE_PERSISTENCE) {
      if (rule.scope === 'tab') expect(rule.clearedOnLogOut).toBe(true);
      if (rule.scope === 'device') {
        expect(rule.clearedOnLogOut).toBe(false);
        expect(rule.stored?.kind).toBe('local');
      }
      const storedInBrowser = rule.scope === 'tab' || rule.scope === 'device';
      expect(rule.stored !== undefined).toBe(storedInBrowser);
    }
  });
});
