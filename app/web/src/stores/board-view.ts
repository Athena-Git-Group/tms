import { create } from 'zustand';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';
import { BOARD_VIEW_STATE } from '../lib/persistence';

/**
 * 看板上「只存在畫面」的選擇：泳道、數量卡篩選、房號搜尋、側欄是開是收。
 * 和登入狀態一樣存在這個分頁的 `sessionStorage`，重新整理還在，登出時要清掉（呼叫 `reset`）。鍵定在 `lib/persistence.ts`。
 *
 * 這裡只有欄位與存取；什麼時候清篩選、側欄各頁預設開或收，由用到的畫面決定。
 */

const LANES = ['none', 'dept', 'floor'] as const;

/** 泳道：不分、依部門、依樓層。 */
export type Lane = (typeof LANES)[number];

const CARD_FILTERS = ['triage', 'pool', 'in_progress'] as const;

/** 可以點來篩選的三張數量卡：待派單、待認領、處理中。 */
export type CardFilter = (typeof CARD_FILTERS)[number];

export interface BoardView {
  lane: Lane;
  /** 目前點了哪一張數量卡；沒有篩選是 `null`。 */
  cardFilter: CardFilter | null;
  /** 「搜尋房號」輸入框的內容。 */
  roomSearch: string;
  /** 使用者自己按過的側欄狀態；還沒按過是 `null`，照各頁的預設。 */
  sidebarOpenByUser: boolean | null;
}

const INITIAL_VIEW: BoardView = {
  lane: 'none',
  cardFilter: null,
  roomSearch: '',
  sidebarOpenByUser: null,
};

interface BoardViewState extends BoardView {
  /** 改其中幾項，並寫回這個分頁的儲存空間。 */
  update: (changes: Partial<BoardView>) => void;
  /** 全部回到初始值，並清掉儲存的那一份。 */
  reset: () => void;
}

export const useBoardViewStore = create<BoardViewState>((set, get) => ({
  ...readStoredView(),
  update: (changes) => {
    set(changes);
    const { lane, cardFilter, roomSearch, sidebarOpenByUser } = get();
    writeJson(BOARD_VIEW_STATE.kind, BOARD_VIEW_STATE.key, {
      lane,
      cardFilter,
      roomSearch,
      sidebarOpenByUser,
    } satisfies BoardView);
  },
  reset: () => {
    removeItem(BOARD_VIEW_STATE.kind, BOARD_VIEW_STATE.key);
    set(INITIAL_VIEW);
  },
}));

function oneOf<T>(allowed: readonly T[], value: unknown): value is T {
  return (allowed as readonly unknown[]).includes(value);
}

/**
 * 儲存空間裡的內容任何人都改得到，畫面拿到認不得的泳道或篩選會畫不出看板。
 * 每一欄各自檢查，是為了一欄壞掉時其他欄的選擇還留得住。
 */
function readStoredView(): BoardView {
  const stored = readJson(BOARD_VIEW_STATE.kind, BOARD_VIEW_STATE.key);
  if (typeof stored !== 'object' || stored === null) return INITIAL_VIEW;
  const view = stored as Record<string, unknown>;
  return {
    lane: oneOf(LANES, view.lane) ? view.lane : INITIAL_VIEW.lane,
    cardFilter: oneOf(CARD_FILTERS, view.cardFilter) ? view.cardFilter : INITIAL_VIEW.cardFilter,
    roomSearch: typeof view.roomSearch === 'string' ? view.roomSearch : INITIAL_VIEW.roomSearch,
    sidebarOpenByUser:
      typeof view.sidebarOpenByUser === 'boolean'
        ? view.sidebarOpenByUser
        : INITIAL_VIEW.sidebarOpenByUser,
  };
}
