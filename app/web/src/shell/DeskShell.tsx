import { useQuery } from '@tanstack/react-query';
import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation } from 'react-router';
import { deskBoardQuery } from '../api/desk';
import { queryClient, retryAfterFailure } from '../api/query-client';
import { AccountPanel } from '../features/account';
import { AnswerPanel } from '../features/answer';
import { DetailPanel } from '../features/detail';
import { AssignToastBar, DispatchNoticeDialog } from '../features/dispatch';
import { boardPollMs, showsConnectionAlert, useLiveUpdates } from '../features/realtime';
import { tileCounts } from '../lib/board-counts';
import { logOut } from '../lib/log-out';
import { PATHS, hotelPath } from '../lib/paths';
import { sidebarOpen } from '../lib/sidebar';
import { useHotelCode } from '../lib/use-hotel-code';
import { BoardSubtitle, RoomSearch } from '../pages/board/BoardPage';
import { TriageSubtitle } from '../pages/triage/TriagePage';
import { useBoardViewStore } from '../stores/board-view';
import { useDeskPanelsStore } from '../stores/desk-panels';
import { useSessionStore } from '../stores/session';
import { ConnectionAlertBar } from './ConnectionAlertBar';
import { visibleUnopenedEntries } from './unopened-entries';

/** 外框的兩頁：總覽看板與待派單頁。 */
type ShellPage = 'board' | 'triage';

/** 側欄導覽由上到下的頁。 */
const SHELL_PAGES: readonly ShellPage[] = ['board', 'triage'];

function shellPageOf(pathname: string): ShellPage {
  return pathname.endsWith(PATHS.triage) ? 'triage' : 'board';
}

/** 外框用到的線條圖示，照雛形 `ui/desk.js` 的同名圖示。 */
const ICON_PATHS = {
  desk: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <circle cx="12" cy="10.5" r="2.2" />
      <path d="M8 16.5a4 4 0 0 1 8 0" />
    </>
  ),
  board: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="M9 5v14M15 5v14" />
    </>
  ),
  tray: (
    <>
      <path d="M3 13l3-7h12l3 7v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <path d="M3 13h5a4 4 0 0 0 8 0h5" />
    </>
  ),
  bell: (
    <>
      <path d="M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z" />
      <path d="M10 20a2 2 0 0 0 4 0" />
    </>
  ),
  bag: (
    <>
      <rect x="5" y="8" width="14" height="12" rx="2.5" />
      <path d="M9 8a3 3 0 0 1 6 0" />
    </>
  ),
  sidebar: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="M9 5v14" />
    </>
  ),
  chevron: <path d="M9 5l7 7-7 7" />,
  plus: <path d="M12 5v14M5 12h14" />,
} satisfies Record<string, ReactNode>;

type IconName = keyof typeof ICON_PATHS;

function Icon({ name, className = 'size-[22px] stroke-[1.8]' }: { name: IconName; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={`shrink-0 fill-none stroke-current ${className}`}>
      {ICON_PATHS[name]}
    </svg>
  );
}

/** 側欄上每一頁的圖示。 */
const PAGE_ICONS: Record<ShellPage, IconName> = { board: 'board', triage: 'tray' };

const ICON_BUTTON =
  'flex size-touch shrink-0 items-center justify-center rounded-full border-none bg-transparent p-0 text-brand';

const NAV_ITEM =
  'group flex min-h-touch items-center gap-3 rounded-control border-none bg-transparent px-3 text-left text-[17px] text-text no-underline';

/**
 * 側欄「待派單」右側的紅色徽章：全館待派單的張數，和數量卡「待派單」是同一個數字。
 * 0 張或看板資料還沒到時整個不畫，不顯示 0。
 */
function TriageBadge() {
  const { data: count } = useQuery({
    ...deskBoardQuery,
    select: (board) => tileCounts(board.tasks, undefined).triage,
  });
  if (!count) return null;
  return (
    <span className="flex h-[22px] min-w-[22px] items-center justify-center rounded-full bg-danger px-[7px] text-[13px] font-bold text-white group-aria-[current=page]:bg-white group-aria-[current=page]:text-danger">
      {count}
    </span>
  );
}

/**
 * 登入後每一頁共用的外框。總覽看板與待派單頁都畫在 `<Outlet />` 的位置，
 * 所以在兩頁之間切換時外框不會重建，掛在外框上的東西也不會被關掉重開。
 *
 * 側欄收起時用 `hidden` 藏起來而不是拿掉，帳號卡等內容一直在頁面上，打開時不必重畫。
 * 還沒自己按過側欄開關時，總覽看板收起、待派單頁打開；按過之後照使用者的選擇。
 * 詳情面板開著時側欄一律收起。
 */
export function DeskShell() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const page = shellPageOf(pathname);
  const session = useSessionStore((state) => state.session);
  const sidebarOpenByUser = useBoardViewStore((state) => state.sidebarOpenByUser);
  const updateView = useBoardViewStore((state) => state.update);
  const [accountPanelOpen, setAccountPanelOpen] = useState(false);
  const detailTaskId = useDeskPanelsStore((state) => state.detailTaskId);
  // 詳情面板開著時側欄讓位收起，關掉面板後回到原本的開收；這不算使用者自己按了側欄開關，所以不改記下的選擇。
  const isSidebarOpen = !detailTaskId && sidebarOpen(page, sidebarOpenByUser);
  const answerTaskId = useDeskPanelsStore((state) => state.answerTaskId);
  const closeDetail = useDeskPanelsStore((state) => state.closeDetail);
  const hotelCode = useHotelCode();

  // 換頁時關掉詳情面板、清掉數量卡篩選，房號搜尋與泳道留著。
  // 點側欄與按瀏覽器的上一頁、下一頁都算換頁，所以看的是頁有沒有變，不是點了哪裡；
  // 重新整理時外框是新掛上的，記下的頁就是現在這頁，篩選還在。
  // 在畫面畫出來之前就清，換回總覽看板時不會先閃一下舊的篩選。
  const shownPage = useRef(page);
  useLayoutEffect(() => {
    if (shownPage.current === page) return;
    shownPage.current = page;
    closeDetail();
    updateView({ cardFilter: null });
  }, [page, closeDetail, updateView]);

  // 事件串流掛在外框：在兩頁之間切換時連線不必斷掉重連，兩頁看到的都是同一份跟著現場更新的看板。
  const live = useLiveUpdates();
  // 兩頁共用同一份看板資料，掛在外框上抓，換頁時不必重抓。
  // 定時再讀也只放在外框，因為頁面的查詢若也各掛一個計時器，同一份資料會被讀好幾次：
  // 即時訊號不可信時照連線狀態給的間隔定時讀，回到可信就停；可信時只有讀取失敗才定時再試。
  const { data: hasBoard, isError: boardReadFailed } = useQuery({
    ...deskBoardQuery,
    select: () => true,
    refetchInterval: boardPollMs(live) ?? retryAfterFailure,
  });
  // 已經有資料之後重讀失敗，畫面留著舊資料，同樣用提醒列告訴櫃檯資料可能延遲。
  const showConnectionAlert = showsConnectionAlert(live) || (hasBoard === true && boardReadFailed);

  return (
    <div data-shell="desk" className="flex h-screen min-h-shell min-w-shell overflow-hidden bg-bg font-sans text-text">
      <nav
        aria-label={t('shell.sidebar')}
        hidden={!isSidebarOpen}
        className="flex w-sidebar shrink-0 flex-col gap-1 border-r-[0.5px] border-line bg-bg px-3 pt-3 pb-4"
      >
        <div className="flex min-h-[52px] items-center gap-2.5 pr-1 pl-2">
          <div className="flex size-[34px] shrink-0 items-center justify-center rounded-[10px] bg-brand text-white">
            <Icon name="desk" className="size-5 stroke-[1.8]" />
          </div>
          <div className="grow">
            <div className="text-[17px] font-bold">{t('app.name')}</div>
            <div className="text-xs text-text-2">{session?.hotelName}</div>
          </div>
          <button
            type="button"
            aria-label={t('shell.collapseSidebar')}
            className={ICON_BUTTON}
            onClick={() => updateView({ sidebarOpenByUser: false })}
          >
            <Icon name="sidebar" />
          </button>
        </div>
        <div className="mt-1.5 flex min-h-0 flex-auto flex-col gap-0.5 overflow-y-auto">
          {SHELL_PAGES.map((target) => (
            <Link
              key={target}
              to={hotelPath(hotelCode, PATHS[target])}
              aria-current={page === target ? 'page' : undefined}
              className={`${NAV_ITEM} aria-[current=page]:bg-brand aria-[current=page]:font-semibold aria-[current=page]:text-white`}
            >
              <Icon name={PAGE_ICONS[target]} className="size-[22px] stroke-[1.8] text-brand group-aria-[current=page]:text-white" />
              <span className="grow">{t(`shell.titles.${target}`)}</span>
              {target === 'triage' && <TriageBadge />}
            </Link>
          ))}
          {visibleUnopenedEntries('sidebarNav', session?.role).map((entry) => (
            <button key={entry.labelKey} type="button" className={NAV_ITEM}>
              <Icon name={entry.icon} className="size-[22px] stroke-[1.8] text-brand" />
              <span className="grow">{t(entry.labelKey)}</span>
            </button>
          ))}
          {visibleUnopenedEntries('sidebarSettings', session?.role).map((entry) => (
            <button
              key={entry.labelKey}
              type="button"
              className="mt-3 flex min-h-touch items-center gap-2 rounded-control border-none bg-transparent px-3 text-left text-[17px] font-semibold text-text-4"
            >
              <span className="grow">{t(entry.labelKey)}</span>
              <Icon name={entry.icon} className="size-4 stroke-[1.8] text-brand" />
            </button>
          ))}
        </div>
        {session && (
          <button
            type="button"
            onClick={() => setAccountPanelOpen(true)}
            className="flex min-h-14 items-center gap-2.5 rounded-card border-none bg-surface px-2.5 py-2 text-left"
          >
            <span
              aria-hidden="true"
              className="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-container text-[15px] font-extrabold text-brand"
            >
              {session.name.slice(-1)}
            </span>
            <span className="grow">
              <span className="block text-[15px] font-semibold">{session.name}</span>
              <span className="block text-xs text-text-2">
                {t(`roles.${session.role}`)} · {session.loginId}
              </span>
            </span>
            <Icon name="chevron" className="size-3.5 stroke-[1.8] text-text-4" />
          </button>
        )}
      </nav>
      <main className="relative flex min-w-0 flex-auto flex-col">
        <header className="flex min-h-toolbar shrink-0 items-center gap-2.5 px-gutter pt-2.5 pb-2">
          {!isSidebarOpen && (
            <button
              type="button"
              aria-label={t('shell.openSidebar')}
              className={ICON_BUTTON}
              onClick={() => {
                // 詳情面板開著時按「打開側欄」，面板讓位給側欄。
                closeDetail();
                updateView({ sidebarOpenByUser: true });
              }}
            >
              <Icon name="sidebar" />
            </button>
          )}
          <div className="grow">
            <h1 className="m-0 text-[28px] leading-[1.15] font-bold whitespace-nowrap">{t(`shell.titles.${page}`)}</h1>
            {page === 'board' ? <BoardSubtitle /> : <TriageSubtitle />}
          </div>
          {page === 'board' && <RoomSearch />}
          {visibleUnopenedEntries('toolbar', session?.role).map((entry) => (
            <button
              key={entry.labelKey}
              type="button"
              className="flex min-h-touch items-center gap-1.5 rounded-full border-none bg-brand px-5 text-base font-bold text-white"
            >
              <Icon name={entry.icon} className="size-4 stroke-[2.4]" />
              {t(entry.labelKey)}
            </button>
          ))}
        </header>
        {showConnectionAlert && <ConnectionAlertBar />}
        <Outlet />
        <AssignToastBar />
      </main>
      {detailTaskId && <DetailPanel key={detailTaskId} taskId={detailTaskId} />}
      {answerTaskId && <AnswerPanel key={answerTaskId} taskId={answerTaskId} />}
      {session && accountPanelOpen && (
        <AccountPanel session={session} onClose={() => setAccountPanelOpen(false)} onLogOut={() => logOut(queryClient)} />
      )}
      <DispatchNoticeDialog />
    </div>
  );
}
