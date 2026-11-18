import { useQuery } from '@tanstack/react-query';
import { useId, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { deskBoardQuery } from '../../api/desk';
import type { DeskTask, Dept, TaskSource } from '../../api/types';
import { LoadFailed } from '../../components/LoadFailed';
import { SkeletonBlock } from '../../components/SkeletonBlock';
import { canDragToDispatch, cellWhileDragging } from '../../lib/drag-dispatch';
import { loadStateOf, type LoadState } from '../../lib/load-state';
import { cardSubtitle, cardTitle } from '../../lib/card-text';
import { countOpenTasks, type BoardColumn } from '../../lib/board-columns';
import { tileCounts, type ColumnHead, type TileCounts, type TileTone } from '../../lib/board-counts';
import {
  boardLayout,
  roomSearchInput,
  type FilterPillContent,
  type LaneKind,
  type LaneRowLayout,
} from '../../lib/board-view-model';
import { displayString } from '../../lib/display-text';
import { correctedNow, dateAtUtcOffset } from '../../lib/server-clock';
import { waitPill, type WaitPillTone } from '../../lib/wait-pill';
import { useBoardViewStore, type CardFilter, type Lane } from '../../stores/board-view';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import { useCorrectedNow, useServerClockStore } from '../../stores/server-clock';
import { boardColumnWidth, boardContentWidth } from '../../lib/board-layout';
import { BoardDragDispatch, DragHint, DropZone, useCardDrag, useDraggingTask } from './board-drag';
import { MoreStatesButton } from './board-more-states';

export function BoardPage() {
  const { data, isError, errorUpdatedAt, refetch } = useQuery(deskBoardQuery);
  const cardFilter = useBoardViewStore((state) => state.cardFilter);
  const roomSearch = useBoardViewStore((state) => state.roomSearch);
  const lane = useBoardViewStore((state) => state.lane);
  const now = useCorrectedNow();
  const headingIdPrefix = useId();
  const hotelTasks = data?.tasks;
  const layout = boardLayout(hotelTasks ?? [], data?.floors ?? [], { cardFilter, roomSearch, lane }, now);
  const headingIdOf = (column: BoardColumn) => `${headingIdPrefix}-${column}`;
  const load = loadStateOf({ data, isError, errorUpdatedAt });
  const scrollerRef = useRef<HTMLDivElement>(null);

  let body: ReactNode;
  if (load === 'failed') {
    body = <LoadFailed onRetry={() => void refetch()} />;
  } else {
    const columns = layout.heads.length;
    body = (
      <div className="relative -mx-gutter flex min-h-0 flex-auto flex-col">
        <div ref={scrollerRef} className="min-h-0 flex-auto overflow-auto [scrollbar-width:thin]">
          <div className="flex flex-col gap-board px-gutter pb-2" style={{ width: boardContentWidth(columns) }}>
            <div className="sticky top-0 z-[2] flex gap-board bg-bg pb-0.5">
              {layout.heads.map((head) => (
                <ColumnHeading
                  key={head.column}
                  id={headingIdOf(head.column)}
                  head={head}
                  columns={columns}
                  loading={load === 'loading'}
                />
              ))}
            </div>
            {load === 'loading' && (
              <div className="flex gap-board">
                {layout.heads.map((head) => (
                  <SkeletonCell key={head.column} labelledBy={headingIdOf(head.column)} columns={columns} />
                ))}
              </div>
            )}
            {load === 'ready' &&
              (layout.laneRows ? (
                layout.laneRows.map((row) => <LaneRow key={row.key} row={row} now={now} />)
              ) : (
                <div className="flex gap-board">
                  {layout.heads.map((head) => (
                    <Cell
                      key={head.column}
                      labelledBy={headingIdOf(head.column)}
                      head={head}
                      columns={columns}
                      lane={null}
                      now={now}
                    />
                  ))}
                </div>
              ))}
          </div>
        </div>
        <MoreStatesButton
          scrollerRef={scrollerRef}
          layoutKey={`${load}:${lane}:${layout.heads.map((head) => head.column).join(',')}`}
        />
      </div>
    );
  }

  return (
    <BoardDragDispatch renderCard={(task) => <TaskCard task={task} now={now} />}>
      <section data-page="board" className="flex min-h-0 flex-auto flex-col gap-2.5 px-gutter pb-4">
        <CountTiles counts={hotelTasks && tileCounts(hotelTasks, now)} load={load} cardFilter={cardFilter} />
        <div className="flex min-h-board-controls shrink-0 items-center gap-2">
          <LaneSwitch />
          <FilterPill content={layout.pill} />
          <DragHint />
        </div>
        {body}
      </section>
    </BoardDragDispatch>
  );
}

function SkeletonCell({ labelledBy, columns }: { labelledBy: string; columns: number }) {
  return (
    <section
      aria-labelledby={labelledBy}
      aria-busy="true"
      className={`flex min-h-[60px] flex-1 basis-0 flex-col gap-2 rounded-card bg-[rgba(118,118,128,0.08)] p-2 ${boardColumnWidth(columns)}`}
    >
      <SkeletonBlock className="h-24 rounded-card" />
      <SkeletonBlock className="h-24 rounded-card" />
    </section>
  );
}

export function RoomSearch() {
  const { t } = useTranslation();
  const roomSearch = useBoardViewStore((state) => state.roomSearch);
  const updateView = useBoardViewStore((state) => state.update);
  return (
    <div className="relative flex items-center">
      <svg
        viewBox="0 0 24 24"
        aria-hidden="true"
        className="pointer-events-none absolute left-3 size-[18px] fill-none stroke-current stroke-[1.7] text-text-4"
      >
        <circle cx="11" cy="11" r="6" />
        <path d="M20 20l-4.5-4.5" />
      </svg>
      <input
        type="search"
        inputMode="numeric"
        maxLength={4}
        aria-label={t('board.roomSearch')}
        placeholder={t('board.roomSearch')}
        autoComplete="off"
        value={roomSearch}
        onChange={(event) => updateView({ roomSearch: roomSearchInput(event.target.value) })}
        className="h-touch w-room-search rounded-control border-none bg-fill pr-3 pl-[38px] text-base text-text outline-none"
      />
    </div>
  );
}

function FilterPill({ content }: { content: FilterPillContent | null }) {
  const { t } = useTranslation();
  const updateView = useBoardViewStore((state) => state.update);
  if (!content) {
    return null;
  }
  const filterText = content.filter && t(`board.filterPill.${content.filter}`);
  const roomText = content.roomNo && t('board.filterPill.room', { roomNo: content.roomNo });
  const text =
    filterText && roomText
      ? t('board.filterPill.both', { filter: filterText, room: roomText })
      : filterText || roomText;
  return (
    <button
      type="button"
      onClick={() => updateView({ cardFilter: null, roomSearch: '' })}
      className="flex min-h-touch items-center gap-1.5 rounded-full border-none bg-brand-container py-0 pr-3 pl-3.5 text-[13px] font-semibold text-brand-on-container"
    >
      {text}
      <svg viewBox="0 0 24 24" aria-hidden="true" className="size-3.5 fill-none stroke-current stroke-[1.7]">
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
  );
}

const FLOOR_LANE_DOT = '#8E8E93';

function LaneRow({ row, now }: { row: LaneRowLayout; now: Date | undefined }) {
  const { t } = useTranslation();
  const headingId = useId();
  const name =
    row.lane.kind === 'dept'
      ? t(`board.card.depts.${row.lane.dept}`)
      : t('board.lanes.floorName', { floor: row.lane.floor });
  const dot = row.lane.kind === 'dept' ? DEPT_COLORS[row.lane.dept].dot : FLOOR_LANE_DOT;
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-1.5">
      <h3 id={headingId} className="sticky left-gutter m-0 flex w-max items-center gap-2 px-1.5 pt-1 font-normal">
        <span className="size-2.5 rounded-[3px]" style={{ backgroundColor: dot }} />
        <b className="text-[15px]">{name}</b>{' '}
        <span className="text-[13px] text-text-2">{t('board.lanes.open', { count: row.openCount })}</span>
      </h3>
      <div className="flex gap-board">
        {row.heads.map((head) => (
          <Cell
            key={head.column}
            label={t('board.lanes.cell', { column: t(`board.columns.${head.column}`), lane: name })}
            head={head}
            columns={row.heads.length}
            lane={row.lane}
            now={now}
          />
        ))}
      </div>
    </section>
  );
}

function businessDay(businessDate: string, language: string): { date: string; weekday: string } {
  const [year = 0, month = 1, day = 1] = businessDate.split('-').map(Number);
  const instant = new Date(Date.UTC(year, month - 1, day));
  const weekday = new Intl.DateTimeFormat(language, { weekday: 'short', timeZone: 'UTC' }).format(instant);
  const date = new Intl.DateTimeFormat(language, { month: 'numeric', day: 'numeric', timeZone: 'UTC' }).format(
    instant,
  );
  return { date, weekday };
}

export function BoardSubtitle() {
  const { t, i18n } = useTranslation();
  const { data } = useQuery(deskBoardQuery);
  const offsetMs = useServerClockStore((state) => state.offsetMs);
  const serverUtcOffsetMinutes = useServerClockStore((state) => state.serverUtcOffsetMinutes);
  const language = i18n.resolvedLanguage ?? i18n.language;
  if (!data) {
    const { date, weekday } = businessDay(todayBeforeBoardData(offsetMs, serverUtcOffsetMinutes), language);
    return <div className="min-h-[17px] truncate text-[13px] text-text-2">{t('board.subtitleDate', { date, weekday })}</div>;
  }
  const { date, weekday } = businessDay(data.businessDate, language);
  return (
    <div className="min-h-[17px] truncate text-[13px] text-text-2">
      {t('board.subtitle', { date, weekday, open: countOpenTasks(data.tasks) })}
    </div>
  );
}

function todayBeforeBoardData(offsetMs: number | null, serverUtcOffsetMinutes: number): string {
  const deviceNow = new Date();
  if (offsetMs === null) {
    return dateAtUtcOffset(deviceNow, -deviceNow.getTimezoneOffset());
  }
  return dateAtUtcOffset(correctedNow(offsetMs, deviceNow), serverUtcOffsetMinutes);
}

const TILE_ICONS = {
  tray: (
    <>
      <path d="M3 13l3-7h12l3 7v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <path d="M3 13h5a4 4 0 0 0 8 0h5" />
    </>
  ),
  pulse: <path d="M3 12h4l2-6 4 12 2-6h6" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
};

type TileIcon = keyof typeof TILE_ICONS;

const TILE_ICON_COLORS: Record<CardFilter | 'longest', string> = {
  triage: 'bg-yellow-bg text-yellow-fg',
  pool: 'bg-brand-container text-brand',
  in_progress: 'bg-ok-bg text-ok-fg',
  longest: 'bg-err-bg text-err-fg',
};

function TileBody({
  icon,
  iconColors,
  number,
  tone,
  label,
}: {
  icon: TileIcon;
  iconColors: string;
  number: string | undefined;
  tone: TileTone;
  label: string;
}) {
  return (
    <>
      <span className={`flex size-11 shrink-0 items-center justify-center rounded-card ${iconColors}`}>
        <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[22px] fill-none stroke-current stroke-[1.8]">
          {TILE_ICONS[icon]}
        </svg>
      </span>
      <span className="flex min-w-0 flex-col">
        {number === undefined ? (
          <span className="block min-h-[29px] py-px">
            <SkeletonBlock className="h-[26px] w-12 rounded-lg" />
          </span>
        ) : (
          <span
            data-tone={tone}
            className="block text-[26px] leading-[1.1] font-extrabold whitespace-nowrap data-[tone=orange]:text-amber data-[tone=red]:text-err-fg"
          >
            {number}
          </span>
        )}
        <span className="line-clamp-3 text-xs leading-[1.3] text-text-2">{label}</span>
      </span>
    </>
  );
}

const TILE_CLASS =
  'flex min-h-count-tile items-center gap-3 rounded-tile border-2 border-transparent bg-surface px-3 py-2.5 text-left shadow-[0_1px_2px_rgba(0,0,0,0.04)]';

function CountTiles({
  counts,
  load,
  cardFilter,
}: {
  counts: TileCounts | undefined;
  load: LoadState;
  cardFilter: CardFilter | null;
}) {
  const { t } = useTranslation();
  const updateView = useBoardViewStore((state) => state.update);
  const tiles: { filter: CardFilter; icon: TileIcon; count: number | undefined; label: string }[] = [
    { filter: 'triage', icon: 'tray', count: counts?.triage, label: t('board.tiles.triage') },
    {
      filter: 'pool',
      icon: 'tray',
      count: counts?.pool,
      label: counts
        ? t('board.tiles.pool', {
            housekeeping: counts.poolByDept.housekeeping,
            engineering: counts.poolByDept.engineering,
          })
        : t('board.tiles.poolLabel'),
    },
    { filter: 'in_progress', icon: 'pulse', count: counts?.inProgress, label: t('board.tiles.inProgress') },
  ];
  const numberOf = (count: number | undefined) =>
    count !== undefined ? String(count) : load === 'failed' ? '—' : undefined;

  return (
    <div className="grid shrink-0 grid-cols-4 gap-2.5">
      {tiles.map((tile) => {
        const number = numberOf(tile.count);
        return (
          <button
            key={tile.filter}
            type="button"
            aria-label={number === undefined ? tile.label : `${number} ${tile.label}`}
            aria-busy={load === 'loading' || undefined}
            aria-pressed={cardFilter === tile.filter}
            onClick={() => updateView({ cardFilter: cardFilter === tile.filter ? null : tile.filter })}
            className={`${TILE_CLASS} aria-pressed:border-brand aria-pressed:shadow-[0_4px_14px_rgba(12,118,128,0.18)]`}
          >
            <TileBody
              icon={tile.icon}
              iconColors={TILE_ICON_COLORS[tile.filter]}
              number={number}
              tone="gray"
              label={tile.label}
            />
          </button>
        );
      })}
      <LongestTile counts={counts} load={load} />
    </div>
  );
}

function LongestTile({ counts, load }: { counts: TileCounts | undefined; load: LoadState }) {
  const { t } = useTranslation();
  const openDetail = useDeskPanelsStore((state) => state.openDetail);
  const longest = counts?.longest;
  let label: string;
  let number: string | undefined;
  if (!counts) {
    label = t('board.tiles.longestLabel');
    number = load === 'failed' ? '—' : undefined;
  } else if (longest) {
    label = t('board.tiles.longest', { roomNo: longest.task.roomNo, title: longest.task.title });
    number = longest.minutes === undefined ? undefined : t('board.tiles.minutes', { minutes: longest.minutes });
  } else {
    label = t('board.tiles.noneWaiting');
    number = '—';
  }
  return (
    <button
      type="button"
      aria-label={number === undefined ? label : `${number} ${label}`}
      aria-busy={load === 'loading' || undefined}
      disabled={!longest}
      onClick={() => longest && openDetail(longest.task.id)}
      className={TILE_CLASS}
    >
      <TileBody
        icon="clock"
        iconColors={TILE_ICON_COLORS.longest}
        number={number}
        tone={longest?.tone ?? 'gray'}
        label={label}
      />
    </button>
  );
}

const LANES: { lane: Lane; key: 'none' | 'dept' | 'floor' }[] = [
  { lane: 'none', key: 'none' },
  { lane: 'dept', key: 'dept' },
  { lane: 'floor', key: 'floor' },
];

function LaneSwitch() {
  const { t } = useTranslation();
  const lane = useBoardViewStore((state) => state.lane);
  const updateView = useBoardViewStore((state) => state.update);
  return (
    <div
      role="group"
      aria-label={t('board.lanes.label')}
      className="flex shrink-0 self-start rounded-[10px] bg-fill p-0.5"
    >
      {LANES.map((option) => (
        <button
          key={option.lane}
          type="button"
          aria-pressed={lane === option.lane}
          onClick={() => updateView({ lane: option.lane })}
          className="min-h-touch min-w-14 rounded-lg border-none bg-transparent px-3.5 text-[13px] aria-pressed:bg-surface aria-pressed:font-semibold aria-pressed:shadow-[0_1px_3px_rgba(0,0,0,0.12)]"
        >
          {t(`board.lanes.${option.key}`)}
        </button>
      ))}
    </div>
  );
}

const COLUMN_DOTS: Record<BoardColumn, string> = {
  triage: '#FF9500',
  pool: '#0C7680',
  accepted: '#8E8E93',
  doing: '#34C759',
  done: '#1A6B34',
  interrupted: '#FF3B30',
  deferred: '#8E8E93',
  closed: '#AEAEB2',
};

function ColumnHeading({
  id,
  head,
  columns,
  loading,
}: {
  id: string;
  head: ColumnHead;
  columns: number;
  loading: boolean;
}) {
  const { t } = useTranslation();
  const { column, count, warning } = head;
  let warningNode: ReactNode = null;
  if (warning && !loading) {
    warningNode = (
      <>
        {' '}
        <span className="text-xs font-bold whitespace-nowrap text-err-fg">
          {t(`board.warnings.${warning.kind}`, { count: warning.count })}
        </span>
      </>
    );
  }
  return (
    <h2
      id={id}
      data-board-head=""
      className={`m-0 min-h-11 flex-1 basis-0 px-1.5 pt-[11px] pb-1 leading-[22px] ${boardColumnWidth(columns)}`}
    >
      <span
        className="mr-1 inline-block size-2.5 rounded-full align-middle"
        style={{ backgroundColor: COLUMN_DOTS[column] }}
      />{' '}
      <span className="text-[15px] font-bold whitespace-nowrap">{t(`board.columns.${column}`)}</span>{' '}
      {!loading && (
        <span className="inline-block min-w-6 rounded-full bg-line-soft px-[7px] text-center text-[13px] font-bold text-text-2">
          {count}
        </span>
      )}
      {warningNode}
    </h2>
  );
}

function laneKey(lane: LaneKind | null): string {
  if (lane === null) return 'all';
  return lane.kind === 'dept' ? lane.dept : `floor-${lane.floor}`;
}

function Cell({
  head,
  columns,
  lane,
  now,
  labelledBy,
  label,
}: {
  head: ColumnHead;
  columns: number;
  lane: LaneKind | null;
  now: Date | undefined;
  labelledBy?: string;
  label?: string;
}) {
  const { t } = useTranslation();
  const { column, tasks } = head;
  const { zones, faded } = cellWhileDragging(column, lane, tasks, useDraggingTask());
  return (
    <section
      aria-labelledby={labelledBy}
      aria-label={label}
      className={`flex min-h-[60px] flex-1 basis-0 flex-col gap-2 rounded-card bg-[rgba(118,118,128,0.08)] p-2 transition-opacity duration-150 ease-out ${boardColumnWidth(columns)} ${faded ? 'opacity-50' : ''}`}
    >
      {zones.map((dept) => (
        <DropZone key={dept} id={`${dept}:${laneKey(lane)}`} dept={dept} />
      ))}
      {tasks.length === 0 ? (
        <span className="px-1 py-2.5 text-center text-[13px] text-text-4">{t(`board.empty.${column}`)}</span>
      ) : (
        tasks.map((task) =>
          canDragToDispatch(task) ? (
            <DraggableTaskCard key={task.id} task={task} now={now} />
          ) : (
            <TaskCard key={task.id} task={task} now={now} />
          ),
        )
      )}
    </section>
  );
}

function DraggableTaskCard({ task, now }: { task: DeskTask; now: Date | undefined }) {
  const drag = useCardDrag(task);
  return <TaskCard task={task} now={now} drag={drag} />;
}

/**
 * 卡片上不能擋掉手指的捲動，否則手指落在卡片上就捲不動看板，所以只擋縮放、長按選字與 iOS 的長按選單。
 */
const DRAGGABLE_CARD_CLASS =
  'cursor-grab shadow-[0_2px_6px_rgba(0,0,0,0.10)] [touch-action:pan-x_pan-y] select-none [-webkit-touch-callout:none]';

const DEPT_COLORS: Record<Dept, { dot: string; fg: string; avatarBg: string; avatarFg: string }> = {
  housekeeping: { dot: '#0C7680', fg: '#0C7680', avatarBg: '#C8E8ED', avatarFg: '#0C7680' },
  engineering: { dot: '#FF8E2B', fg: '#944B00', avatarBg: '#FFDCC5', avatarFg: '#703700' },
  frontdesk: { dot: '#AEAEB2', fg: '#3C3C43', avatarBg: '#E5E5EA', avatarFg: '#3C3C43' },
};

const SOURCE_ICONS: Record<TaskSource, ReactNode> = {
  guest: (
    <>
      <rect x="4" y="4" width="6" height="6" rx="1" />
      <rect x="14" y="4" width="6" height="6" rx="1" />
      <rect x="4" y="14" width="6" height="6" rx="1" />
      <path d="M14 14h2v2h-2zM18 18h2v2h-2zM18 14h2M14 18v2" />
    </>
  ),
  desk: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <circle cx="12" cy="10.5" r="2.2" />
      <path d="M8 16.5a4 4 0 0 1 8 0" />
    </>
  ),
  system: (
    <>
      <rect x="4" y="6" width="16" height="14" rx="2.5" />
      <path d="M4 10h16M8 4v4M16 4v4" />
    </>
  ),
  staff: <path d="M14.5 6.5a4 4 0 0 0 5 5L11 20a2.1 2.1 0 0 1-3-3l8.5-8.5" />,
};

const PILL_COLORS: Record<WaitPillTone, string> = {
  gray: 'bg-bg text-text-2',
  orange: 'bg-warm-bg text-warm-fg',
  red: 'bg-err-bg text-err-fg',
  green: 'bg-ok-bg text-ok-fg',
};

function WaitPillBadge({ text, tone }: { text: string; tone: WaitPillTone }) {
  return (
    <span
      data-tone={tone}
      className={`inline-flex items-center gap-[3px] rounded-full px-2 py-0.5 text-xs font-bold whitespace-nowrap ${PILL_COLORS[tone]}`}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true" className="size-3 fill-none stroke-current stroke-2">
        {TILE_ICONS.clock}
      </svg>
      {text}
    </span>
  );
}

function TaskCard({
  task,
  now,
  drag,
}: {
  task: DeskTask;
  now: Date | undefined;
  drag?: { ref: (element: Element | null) => void; source: boolean };
}) {
  const { t } = useTranslation();
  const openDetail = useDeskPanelsStore((state) => state.openDetail);
  const opened = useDeskPanelsStore((state) => state.detailTaskId === task.id);
  const serverUtcOffsetMinutes = useServerClockStore((state) => state.serverUtcOffsetMinutes);
  const title = cardTitle(task, t);
  const subtitle = cardSubtitle(task, t);
  const colors = DEPT_COLORS[task.dept];
  const sop = task.state === 'doing' && task.sop && task.sop.total > 0 ? task.sop : null;
  const pill = now && waitPill(task, now, serverUtcOffsetMinutes);
  const pillText = pill ? displayString(pill.text, t) : '';
  return (
    <button
      ref={drag?.ref}
      type="button"
      aria-label={pill ? t('board.card.name', { roomNo: task.roomNo, title, wait: pillText }) : `${task.roomNo} ${title}`}
      aria-expanded={opened}
      onClick={() => openDetail(task.id)}
      className={`flex w-full flex-col gap-[7px] rounded-card border-none bg-surface p-3 text-left aria-expanded:outline-2 aria-expanded:-outline-offset-2 aria-expanded:outline-brand ${drag ? DRAGGABLE_CARD_CLASS : 'shadow-[0_1px_2px_rgba(0,0,0,0.06)]'} ${drag?.source ? 'opacity-40' : ''}`}
    >
      <span className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
        <span className="text-[22px] font-extrabold tracking-[-0.3px]">{task.roomNo}</span>
        {task.vip && (
          <span className="rounded-md bg-warm-bg px-1.5 py-px text-[11px] font-extrabold text-warm-fg">{t('board.card.vip')}</span>
        )}
        {pill && <span aria-hidden="true" className="min-w-0 flex-auto" />}
        {pill && <WaitPillBadge text={pillText} tone={pill.tone} />}
      </span>
      <span className="line-clamp-2 text-[15px] leading-[1.35] font-semibold [overflow-wrap:anywhere]">{title}</span>
      {subtitle && (
        <span className="line-clamp-2 text-xs leading-[1.4] text-text-2 [overflow-wrap:anywhere]">{subtitle}</span>
      )}
      {sop && (
        <span className="flex items-center gap-2 text-xs text-text-2">
          <span
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={sop.total}
            aria-valuenow={sop.done}
            className="h-1.5 flex-auto overflow-hidden rounded-full bg-line-soft"
          >
            <i
              className="block h-full rounded-full bg-brand"
              style={{ width: `${Math.round((sop.done / sop.total) * 100)}%` }}
            />
          </span>
          {t('board.card.sop', { done: sop.done, total: sop.total })}
        </span>
      )}
      <span className="flex items-center gap-1.5">
        <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: colors.dot }} />
        <span className="flex min-w-0 flex-auto items-baseline gap-1 text-xs whitespace-nowrap">
          <b className="shrink-0" style={{ color: colors.fg }}>
            {t(`board.card.depts.${task.dept}`)}
          </b>
          <span className="min-w-0 truncate text-text-2">· {t(`board.card.types.${task.type}`)}</span>
        </span>
        <svg
          viewBox="0 0 24 24"
          role="img"
          aria-label={t(`board.card.sources.${task.source}`)}
          className="size-[15px] shrink-0 fill-none stroke-current stroke-[1.8] text-text-4"
        >
          {SOURCE_ICONS[task.source]}
        </svg>
        {task.assigneeName && (
          <span
            role="img"
            aria-label={t('board.card.assignee', { name: task.assigneeName })}
            className="flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-extrabold"
            style={{ backgroundColor: colors.avatarBg, color: colors.avatarFg }}
          >
            {task.assigneeName.slice(-1)}
          </span>
        )}
      </span>
    </button>
  );
}
