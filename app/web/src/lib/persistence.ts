import type { StorageKind } from './browser-storage';

/**
 * 櫃檯畫面上每一樣狀態「重新整理、關掉分頁、登出之後還在不在」，都在這個檔決定。
 *
 * 櫃檯的裝置是大家輪流用的，所以去處只有四種，各有固定的意思：
 * - 跟著分頁：存在這個分頁的 `sessionStorage`。重新整理還在，關掉分頁就沒了，每個分頁各自一份，登出時清掉。
 * - 跟著裝置：存在這台裝置的 `localStorage`。關掉瀏覽器還在，分頁之間共用，登出也不清。
 * - 跟著網址：停在哪一頁、哪一家飯店，靠網址本身記住。
 * - 只在記憶體：重新整理就不見了。
 *
 * 新增一樣狀態時，先在 `STATE_PERSISTENCE` 寫下它屬於哪一種，存到瀏覽器的再到下面加一個鍵，
 * store 一律從這裡拿鍵與儲存種類，不自己寫字串。
 * 鍵的字串要維持原樣，因為使用者的分頁或裝置上可能已經用這個鍵存著內容，換了字串就讀不回來。
 */

/** 存在瀏覽器裡、鍵是固定字串的一樣狀態。 */
export interface StoredState {
  readonly kind: StorageKind;
  readonly key: string;
}

/** 每個人各存一筆的狀態，實際的鍵是前綴接上員工編號。 */
export interface StoredStatePerStaff {
  readonly kind: StorageKind;
  readonly keyPrefix: string;
}

/** 登入狀態：後端登入回應的 JSON。跟著分頁。 */
export const SESSION_STATE = {
  kind: 'session',
  key: 'tms.frontdesk.session',
} as const satisfies StoredState;

/** 看板上的選擇：泳道、數量卡篩選、房號搜尋、自己按過的側欄開收。跟著分頁。 */
export const BOARD_VIEW_STATE = {
  kind: 'session',
  key: 'tms.frontdesk.board-view',
} as const satisfies StoredState;

/** 登入頁現在用的語言。登入前沒有身分，所以另記一筆。跟著裝置。 */
export const LOGIN_PAGE_LANGUAGE_STATE = {
  kind: 'local',
  key: 'tms.frontdesk.language.login-page',
} as const satisfies StoredState;

/** 每個人自己的介面語言，例如 `tms.frontdesk.language.staff.F0101`。跟著裝置。 */
export const STAFF_LANGUAGE_STATE = {
  kind: 'local',
  keyPrefix: 'tms.frontdesk.language.staff.',
} as const satisfies StoredStatePerStaff;

/** 跟著分頁的鍵，也就是登出時一定要清掉的那幾個。 */
export const TAB_STORED_STATES: readonly StoredState[] = [SESSION_STATE, BOARD_VIEW_STATE];

export type PersistenceScope = 'tab' | 'device' | 'url' | 'memory';

export interface PersistenceRule {
  /** 是哪一樣狀態。 */
  readonly state: string;
  readonly scope: PersistenceScope;
  /** 存在瀏覽器時的鍵；跟著網址或只在記憶體的沒有。 */
  readonly stored?: StoredState | StoredStatePerStaff;
  /** 重新整理之後畫面看到的樣子。 */
  readonly afterReload: string;
  readonly clearedOnLogOut: boolean;
}

/**
 * 「重新整理後保留什麼」的完整清單，內容和畫面規劃那張表一致。
 * 只放在記憶體的狀態也列進來，接手的人才能在同一個地方看到全部的去留，不必逐一翻各個 store。
 */
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
    // 時間差與時區不屬於哪一個人，下一位登入後讀到看板就會重算，所以登出不清。
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
