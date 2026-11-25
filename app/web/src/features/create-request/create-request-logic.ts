import { ApiError, NetworkError } from '../../api/client';
import type {
  DeskCreateRequestDept,
  DeskCreateRequestsRequest,
  DeskMenuKey,
  DeskRoom,
  DeskTask,
} from '../../api/types';

export type MenuItemLabelKey =
  | 'towel'
  | 'bathTowel'
  | 'water'
  | 'slippers'
  | 'toothbrushKit'
  | 'crib'
  | 'extraBed'
  | 'ice'
  | 'airConditioner'
  | 'hotWater'
  | 'lights'
  | 'tv';

export interface MenuItem {
  key: DeskMenuKey;
  labelKey: MenuItemLabelKey;
  dept: DeskCreateRequestDept;
  max: number;
}

export const MENU_ITEMS: readonly MenuItem[] = [
  { key: '毛巾', labelKey: 'towel', dept: 'housekeeping', max: 9 },
  { key: '浴巾', labelKey: 'bathTowel', dept: 'housekeeping', max: 9 },
  { key: '礦泉水', labelKey: 'water', dept: 'housekeeping', max: 9 },
  { key: '拖鞋', labelKey: 'slippers', dept: 'housekeeping', max: 9 },
  { key: '牙刷組', labelKey: 'toothbrushKit', dept: 'housekeeping', max: 9 },
  { key: '嬰兒床', labelKey: 'crib', dept: 'housekeeping', max: 2 },
  { key: '加床', labelKey: 'extraBed', dept: 'housekeeping', max: 2 },
  { key: '冰塊', labelKey: 'ice', dept: 'housekeeping', max: 2 },
  { key: '冷氣', labelKey: 'airConditioner', dept: 'engineering', max: 1 },
  { key: '熱水', labelKey: 'hotWater', dept: 'engineering', max: 1 },
  { key: '燈具', labelKey: 'lights', dept: 'engineering', max: 1 },
  { key: '電視', labelKey: 'tv', dept: 'engineering', max: 1 },
];

export const ROOM_NO_MAX_LENGTH = 4;
export const OTHER_TEXT_MAX_LENGTH = 60;
export const SUGGESTION_LIMIT = 6;

export type RoomStatus =
  | { kind: 'unknown' }
  | { kind: 'invalid' }
  | { kind: 'occupied'; room: DeskRoom }
  | { kind: 'vacant'; roomNo: string }
  | { kind: 'unchecked'; roomNo: string };

export interface CreateRequestDraft {
  roomNo: string;
  quantities: Partial<Record<DeskMenuKey, number>>;
  otherText: string;
  otherDept: DeskCreateRequestDept;
}

export const EMPTY_DRAFT: CreateRequestDraft = {
  roomNo: '',
  quantities: {},
  otherText: '',
  otherDept: 'housekeeping',
};

export type SubmitFailure = 'room_not_found' | 'forbidden' | 'invalid' | 'not_sent';

export function filterRoomInput(raw: string): string {
  return raw.replace(/[^0-9]/g, '').slice(0, ROOM_NO_MAX_LENGTH);
}

export function roomStatus(roomNo: string, rooms: DeskRoom[] | undefined, roomsFailed: boolean): RoomStatus {
  if (roomNo === '') return { kind: 'invalid' };
  if (!rooms) return roomsFailed ? { kind: 'unchecked', roomNo } : { kind: 'unknown' };
  const room = rooms.find((candidate) => candidate.roomNo === roomNo);
  if (!room) return { kind: 'invalid' };
  return room.occupied && room.guest ? { kind: 'occupied', room } : { kind: 'vacant', roomNo };
}

export function isValidRoom(status: RoomStatus): boolean {
  return status.kind === 'occupied' || status.kind === 'vacant' || status.kind === 'unchecked';
}

export function suggestRooms(typed: string, rooms: DeskRoom[]): DeskRoom[] {
  return rooms
    .filter((room) => room.occupied && room.guest && room.roomNo.startsWith(typed))
    .sort((a, b) => Number(a.roomNo) - Number(b.roomNo) || a.roomNo.localeCompare(b.roomNo))
    .slice(0, SUGGESTION_LIMIT);
}

export type RoomFeedback =
  | { kind: 'guest'; room: DeskRoom }
  | { kind: 'vacant'; roomNo: string }
  | { kind: 'loading' }
  | { kind: 'listFailed' }
  | { kind: 'suggestions'; rooms: DeskRoom[] }
  | { kind: 'none' };

export function roomFeedback(
  roomNo: string,
  status: RoomStatus,
  list: { rooms: DeskRoom[] | undefined; loading: boolean; failed: boolean },
): RoomFeedback {
  if (status.kind === 'occupied') return { kind: 'guest', room: status.room };
  if (status.kind === 'vacant') return { kind: 'vacant', roomNo: status.roomNo };
  if (list.loading) return { kind: 'loading' };
  if (!list.rooms) return list.failed ? { kind: 'listFailed' } : { kind: 'none' };
  if (status.kind !== 'invalid') return { kind: 'none' };
  const rooms = suggestRooms(roomNo, list.rooms);
  return rooms.length === 0 ? { kind: 'none' } : { kind: 'suggestions', rooms };
}

export function monthDay(isoDate: string): string {
  const [, month, day] = isoDate.split('-');
  return `${Number(month)}/${Number(day)}`;
}

export function avatarOf(name: string): string {
  return Array.from(name.trim())[0] ?? '';
}

export function quantityOf(draft: CreateRequestDraft, key: DeskMenuKey): number {
  return draft.quantities[key] ?? 0;
}

export function withQuantity(draft: CreateRequestDraft, item: MenuItem, quantity: number): CreateRequestDraft {
  const next = Math.min(item.max, Math.max(0, quantity));
  const quantities = { ...draft.quantities };
  if (next === 0) delete quantities[item.key];
  else quantities[item.key] = next;
  return { ...draft, quantities };
}

export function hasOtherText(draft: CreateRequestDraft): boolean {
  return draft.otherText.trim() !== '';
}

export function clampOtherText(text: string): string {
  return Array.from(text).slice(0, OTHER_TEXT_MAX_LENGTH).join('');
}

export function nextOtherText(current: string, typed: string): string {
  const full = Array.from(current).length >= OTHER_TEXT_MAX_LENGTH;
  if (full && Array.from(typed).length > OTHER_TEXT_MAX_LENGTH) return current;
  return clampOtherText(typed);
}

export function chosenItems(draft: CreateRequestDraft): { item: MenuItem; quantity: number }[] {
  return MENU_ITEMS.filter((item) => quantityOf(draft, item.key) > 0).map((item) => ({
    item,
    quantity: quantityOf(draft, item.key),
  }));
}

export function ticketCount(draft: CreateRequestDraft): number {
  return chosenItems(draft).length + (hasOtherText(draft) ? 1 : 0);
}

export function canSubmit(draft: CreateRequestDraft, status: RoomStatus): boolean {
  return isValidRoom(status) && ticketCount(draft) > 0;
}

export interface SummaryWords {
  itemName: (item: MenuItem) => string;
  other: string;
  listSeparator: string;
}

export interface SummaryLists {
  housekeeping: string | null;
  engineering: string | null;
}

export function summaryLists(draft: CreateRequestDraft, words: SummaryWords): SummaryLists | null {
  if (ticketCount(draft) === 0) return null;
  const lists: Record<DeskCreateRequestDept, string[]> = {
    housekeeping: [],
    engineering: [],
  };
  for (const { item, quantity } of chosenItems(draft)) {
    const name = words.itemName(item);
    lists[item.dept].push(item.dept === 'housekeeping' ? `${name} ×${quantity}` : name);
  }
  if (hasOtherText(draft)) lists[draft.otherDept].push(words.other);
  const join = (names: string[]) => (names.length === 0 ? null : names.join(words.listSeparator));
  return {
    housekeeping: join(lists.housekeeping),
    engineering: join(lists.engineering),
  };
}

export function ticketTitle(menuKey: DeskMenuKey, quantity: number): string {
  const item = MENU_ITEMS.find((candidate) => candidate.key === menuKey);
  if (item?.dept === 'engineering') return `${menuKey}有問題`;
  return menuKey === '冰塊' ? `冰塊 ×${quantity} 桶` : `${menuKey} ×${quantity}`;
}

export function createRequestBody(draft: CreateRequestDraft): DeskCreateRequestsRequest {
  return {
    roomNo: draft.roomNo,
    items: chosenItems(draft).map(({ item, quantity }) => ({
      menuKey: item.key,
      quantity,
    })),
    other: hasOtherText(draft) ? { text: draft.otherText.trim(), dept: draft.otherDept } : null,
  };
}

const OPEN_STATES = ['triage', 'pool', 'accepted', 'doing'] as const;

type OpenState = (typeof OPEN_STATES)[number];

type OpenTask = DeskTask & { state: OpenState };

function isOpen(task: DeskTask): task is OpenTask {
  return (OPEN_STATES as readonly string[]).includes(task.state);
}

export function similarTasks(draft: CreateRequestDraft, boardTasks: DeskTask[]): OpenTask[] {
  const chosenKeys = new Set<string>(chosenItems(draft).map(({ item }) => item.key));
  const withOther = hasOtherText(draft);
  return boardTasks
    .filter(isOpen)
    .filter((task) => task.roomNo === draft.roomNo)
    .filter((task) => (task.menuKey != null && chosenKeys.has(task.menuKey)) || (withOther && task.guestTyped))
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
}

export interface SimilarTaskLine {
  taskId: string;
  title: string;
  state: OpenState;
  assigneeName: string | null;
  minutes: number;
}

export function similarTaskLines(draft: CreateRequestDraft, boardTasks: DeskTask[], now: Date): SimilarTaskLine[] {
  return similarTasks(draft, boardTasks).map((task) => ({
    taskId: task.id,
    title: similarTaskTitle(task),
    state: task.state,
    assigneeName: task.assigneeName ?? null,
    minutes: minutesSince(task.createdAt, now),
  }));
}

export function similarTaskTitle(task: DeskTask): string {
  if (!task.guestTyped) return task.title;
  return task.guestText?.translated || task.guestText?.original || task.title;
}

export function minutesSince(createdAt: string, now: Date): number {
  return Math.max(0, Math.floor((now.getTime() - Date.parse(createdAt)) / 60_000));
}

export function isTimeout(failure: unknown): boolean {
  return failure instanceof DOMException && (failure.name === 'AbortError' || failure.name === 'TimeoutError');
}

export function isUnauthorized(failure: unknown): boolean {
  return failure instanceof ApiError && failure.status === 401;
}

export function keepIdempotencyKey(failure: unknown): boolean {
  if (failure instanceof NetworkError || isTimeout(failure)) return true;
  return failure instanceof ApiError && failure.status >= 500;
}

export function submitFailureKind(failure: unknown): SubmitFailure {
  if (!(failure instanceof ApiError) || failure.status >= 500) return 'not_sent';
  if (failure.status === 404 || failure.code === 'not_found') return 'room_not_found';
  if (failure.status === 403 || failure.code === 'forbidden') return 'forbidden';
  return 'invalid';
}
