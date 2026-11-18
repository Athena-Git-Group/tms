import { create } from 'zustand';
import { readJson, removeItem, writeJson } from '../lib/browser-storage';
import { BOARD_VIEW_STATE } from '../lib/persistence';

const LANES = ['none', 'dept', 'floor'] as const;

export type Lane = (typeof LANES)[number];

const CARD_FILTERS = ['triage', 'pool', 'in_progress'] as const;

export type CardFilter = (typeof CARD_FILTERS)[number];

export interface BoardView {
  lane: Lane;
  cardFilter: CardFilter | null;
  roomSearch: string;
  sidebarOpenByUser: boolean | null;
}

const INITIAL_VIEW: BoardView = {
  lane: 'none',
  cardFilter: null,
  roomSearch: '',
  sidebarOpenByUser: null,
};

interface BoardViewState extends BoardView {
  update: (changes: Partial<BoardView>) => void;
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
