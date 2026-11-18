import type { DeskTask, Dept } from '../api/types';
import type { CardFilter, Lane } from '../stores/board-view';
import { columnsShownFor, countOpenTasks, type BoardColumn } from './board-columns';
import { columnHeads, type ColumnHead } from './board-counts';

export function roomSearchInput(typed: string): string {
  return typed.replace(/\D/g, '').slice(0, 4);
}

export function searchByRoom(tasks: readonly DeskTask[], roomSearch: string): DeskTask[] {
  return tasks.filter((task) => task.roomNo.startsWith(roomSearch));
}

export const LANE_DEPTS: readonly Dept[] = ['frontdesk', 'housekeeping', 'engineering'];

export type LaneKind = { kind: 'dept'; dept: Dept } | { kind: 'floor'; floor: number };

export interface LaneRowLayout {
  key: string;
  lane: LaneKind;
  openCount: number;
  heads: ColumnHead[];
}

export interface FilterPillContent {
  filter: CardFilter | null;
  roomNo: string | null;
}

export interface BoardLayout {
  columns: readonly BoardColumn[];
  heads: ColumnHead[];
  laneRows: LaneRowLayout[] | null;
  pill: FilterPillContent | null;
}

export interface BoardViewInput {
  cardFilter: CardFilter | null;
  roomSearch: string;
  lane: Lane;
}

export function filterPillContent(cardFilter: CardFilter | null, roomSearch: string): FilterPillContent | null {
  if (!cardFilter && !roomSearch) {
    return null;
  }
  return { filter: cardFilter, roomNo: roomSearch || null };
}

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
