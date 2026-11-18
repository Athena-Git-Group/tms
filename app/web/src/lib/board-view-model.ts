import type { DeskTask, Dept } from '../api/types';
import type { CardFilter, Lane } from '../stores/board-view';
import { columnsShownFor, countOpenTasks, type BoardColumn } from './board-columns';
import { columnHeads, type ColumnHead } from './board-counts';

/**
 * 總覽看板要畫的整份結構：把全館的單、數量卡篩選、房號搜尋與泳道組在一起，畫面只照這份結構畫。
 * 寫成不碰畫面的純計算，欄頭、泳道副標與篩選膠囊對「哪些單算數」才不會各寫各的。
 *
 * 範圍：數量卡篩選只決定留下哪幾欄，不減少單；房號搜尋才減少單。欄頭與泳道副標都算搜尋後的單，
 * 泳道副標不管數量卡篩選留下哪幾欄。全館的數字（數量卡、頁副標）不經過這裡。
 */

/** 「搜尋房號」只收數字、最多 4 碼：打進其他字元或第 5 碼時，框裡不會出現。 */
export function roomSearchInput(typed: string): string {
  return typed.replace(/\D/g, '').slice(0, 4);
}

/** 房號以搜尋的數字開頭才留下；沒有搜尋時全部留下。 */
export function searchByRoom(tasks: readonly DeskTask[], roomSearch: string): DeskTask[] {
  return tasks.filter((task) => task.roomNo.startsWith(roomSearch));
}

/** 部門泳道由上到下的順序：未分部門、房務、工務。 */
export const LANE_DEPTS: readonly Dept[] = ['frontdesk', 'housekeeping', 'engineering'];

/** 一列泳道是哪一種：某個部門，或某一樓層。名稱與色點由畫面照這個決定。 */
export type LaneKind = { kind: 'dept'; dept: Dept } | { kind: 'floor'; floor: number };

/** 一列泳道：它是哪一種、副標的未結案張數、這一列各欄的單。 */
export interface LaneRowLayout {
  key: string;
  lane: LaneKind;
  /** 這一列（搜尋後）不在「已結案」欄的單，不管數量卡篩選。 */
  openCount: number;
  heads: ColumnHead[];
}

/** 篩選膠囊的內容：數量卡篩選與房號搜尋，沒有的那一樣是 `null`。兩樣都沒有時沒有膠囊。 */
export interface FilterPillContent {
  filter: CardFilter | null;
  roomNo: string | null;
}

export interface BoardLayout {
  columns: readonly BoardColumn[];
  /** 看板上緣黏住的那一列欄頭，算搜尋後的全部單，不分泳道。 */
  heads: ColumnHead[];
  /** 不分泳道時是 `null`，用 `heads` 畫一列格子；分泳道時是各列。 */
  laneRows: LaneRowLayout[] | null;
  pill: FilterPillContent | null;
}

export interface BoardViewInput {
  cardFilter: CardFilter | null;
  roomSearch: string;
  lane: Lane;
}

/** 篩選膠囊：兩樣都沒有時不顯示。 */
export function filterPillContent(cardFilter: CardFilter | null, roomSearch: string): FilterPillContent | null {
  if (!cardFilter && !roomSearch) {
    return null;
  }
  return { filter: cardFilter, roomNo: roomSearch || null };
}

/**
 * 依泳道把搜尋後的單分成幾列。部門泳道固定三列；樓層泳道照後端給的飯店樓層由低到高，那一層沒有單也有一列。
 */
function laneRows(
  lane: Exclude<Lane, 'none'>,
  searchedTasks: readonly DeskTask[],
  floors: readonly number[],
  columns: readonly BoardColumn[],
  now: Date | undefined,
): LaneRowLayout[] {
  const row = (key: string, kind: LaneKind, tasks: DeskTask[]): LaneRowLayout => ({
    key,
    lane: kind,
    openCount: countOpenTasks(tasks),
    heads: columnHeads(tasks, columns, now),
  });
  if (lane === 'dept') {
    return LANE_DEPTS.map((dept) =>
      row(
        dept,
        { kind: 'dept', dept },
        searchedTasks.filter((task) => task.dept === dept),
      ),
    );
  }
  return [...floors]
    .sort((a, b) => a - b)
    .map((floor) =>
      row(
        `floor-${floor}`,
        { kind: 'floor', floor },
        searchedTasks.filter((task) => task.floor === floor),
      ),
    );
}

/** 全館的單加上目前的篩選、搜尋與泳道，算出看板要畫的結構。`hotelTasks` 傳全館的單，還沒讀到時傳空陣列。 */
export function boardLayout(
  hotelTasks: readonly DeskTask[],
  floors: readonly number[],
  view: BoardViewInput,
  now: Date | undefined,
): BoardLayout {
  const searchedTasks = searchByRoom(hotelTasks, view.roomSearch);
  const columns = columnsShownFor(view.cardFilter);
  return {
    columns,
    heads: columnHeads(searchedTasks, columns, now),
    laneRows: view.lane === 'none' ? null : laneRows(view.lane, searchedTasks, floors, columns, now),
    pill: filterPillContent(view.cardFilter, view.roomSearch),
  };
}
