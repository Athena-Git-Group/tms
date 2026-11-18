import type { StorageKind } from './browser-storage';

export interface StoredState {
  readonly kind: StorageKind;
  readonly key: string;
}

export interface StoredStatePerStaff {
  readonly kind: StorageKind;
  readonly keyPrefix: string;
}

export const SESSION_STATE = {
  kind: 'session',
  key: 'tms.frontdesk.session',
} as const satisfies StoredState;

export const BOARD_VIEW_STATE = {
  kind: 'session',
  key: 'tms.frontdesk.board-view',
} as const satisfies StoredState;

export const LOGIN_PAGE_LANGUAGE_STATE = {
  kind: 'local',
  key: 'tms.frontdesk.language.login-page',
} as const satisfies StoredState;

export const STAFF_LANGUAGE_STATE = {
  kind: 'local',
  keyPrefix: 'tms.frontdesk.language.staff.',
} as const satisfies StoredStatePerStaff;

export const TAB_STORED_STATES: readonly StoredState[] = [SESSION_STATE, BOARD_VIEW_STATE];

export type PersistenceScope = 'tab' | 'device' | 'url' | 'memory';

export interface PersistenceRule {
  readonly state: string;
  readonly scope: PersistenceScope;
  readonly stored?: StoredState | StoredStatePerStaff;
  readonly afterReload: string;
  readonly clearedOnLogOut: boolean;
}

export const STATE_PERSISTENCE: readonly PersistenceRule[] = [
  {
    state: '登入狀態（含還沒設定新密碼的標記）',
    scope: 'tab',
    stored: SESSION_STATE,
    afterReload: '保留。還沒設定新密碼的人停在改密碼畫面，兩欄清空。',
    clearedOnLogOut: true,
  },
  {
    state: '停在哪一頁、哪一家飯店',
    scope: 'url',
    afterReload: '保留。',
    clearedOnLogOut: false,
  },
  {
    state: '數量卡篩選、房號搜尋、泳道',
    scope: 'tab',
    stored: BOARD_VIEW_STATE,
    afterReload: '保留。',
    clearedOnLogOut: true,
  },
  {
    state: '側欄是開是收',
    scope: 'tab',
    stored: BOARD_VIEW_STATE,
    afterReload: '自己按過的保留，沒按過的照各頁預設。',
    clearedOnLogOut: true,
  },
  {
    state: '詳情面板、當場回答面板開著哪一張單',
    scope: 'memory',
    afterReload: '不保留，重新整理後都是關的。',
    clearedOnLogOut: true,
  },
  {
    state: '派單浮條（5 秒內可以復原的那一次派單）',
    scope: 'memory',
    afterReload: '不保留，重新整理後不能在這裡復原。',
    clearedOnLogOut: true,
  },
  {
    state: '看板上正在拖哪一張卡片（拖曳中的提示、拖放區、其他格變淡）',
    scope: 'memory',
    afterReload: '不保留，重新整理後沒有在拖。',
    clearedOnLogOut: true,
  },
  {
    state: '帳號面板、對話框是否開著',
    scope: 'memory',
    afterReload: '不保留，重新整理後都是關的。',
    clearedOnLogOut: true,
  },
  {
    state: '留給登入頁的提示',
    scope: 'memory',
    afterReload: '不保留，登入頁照一般的樣子顯示。',
    clearedOnLogOut: true,
  },
  {
    state: '抓過的後端資料',
    scope: 'memory',
    afterReload: '不保留，重新向後端讀。',
    clearedOnLogOut: true,
  },
  {
    state: '後端與這台裝置的時間差（校正「現在」用）與後端的時區（顯示「時:分」用）',
    scope: 'memory',
    afterReload: '不保留，讀到後端資料時重算。',
    clearedOnLogOut: false,
  },
  {
    state: '登入頁現在的語言',
    scope: 'device',
    stored: LOGIN_PAGE_LANGUAGE_STATE,
    afterReload: '保留，分頁之間共用。',
    clearedOnLogOut: false,
  },
  {
    state: '每個人的介面語言',
    scope: 'device',
    stored: STAFF_LANGUAGE_STATE,
    afterReload: '保留，分頁之間共用。',
    clearedOnLogOut: false,
  },
];
