import { useLayoutEffect, useState, type RefObject } from 'react';
import { useTranslation } from 'react-i18next';
import { columnsHiddenOnRight } from '../../lib/board-layout';

/** 看板本體裡各欄的欄頭，由左到右。欄頭身上帶 `data-board-head`（`BoardPage` 的 `ColumnHeading`）。 */
function headsIn(scroller: HTMLElement): HTMLElement[] {
  return [...scroller.querySelectorAll<HTMLElement>('[data-board-head]')];
}

/** 看板本體右邊現在看不到幾欄。看得到的範圍扣掉直向捲軸。 */
function measureHidden(scroller: HTMLElement): number {
  const box = scroller.getBoundingClientRect();
  const visibleRight = box.left + scroller.clientLeft + scroller.clientWidth;
  return columnsHiddenOnRight(
    headsIn(scroller).map((head) => head.getBoundingClientRect().right),
    visibleRight,
  );
}

/** 看板往右捲一欄：一欄的寬度含欄距，量相鄰兩個欄頭左緣的距離。 */
function scrollOneColumn(scroller: HTMLElement) {
  const [first, second] = headsIn(scroller);
  if (!first) return;
  const gap = first.parentElement ? parseFloat(getComputedStyle(first.parentElement).columnGap) || 0 : 0;
  const step = second
    ? second.getBoundingClientRect().left - first.getBoundingClientRect().left
    : first.getBoundingClientRect().width + gap;
  scroller.scrollBy({ left: step, behavior: 'smooth' });
}

/**
 * 看板右緣的「還有 {N} 個狀態」與底下的淡出，照雛形 `ui/desk.js` 的 `updateMore`：
 * N 是看板右邊看不到的欄數，按一下往右捲一欄；N 是 0（全部看得到或捲到最右邊）時整個不畫。
 * 看板捲動、看板本體或欄的寬度改變（視窗改尺寸、側欄或詳情面板開關、篩選）時重算。
 *
 * @param scrollerRef 看板本體，也就是可以捲動的那一塊
 * @param layoutKey 欄或泳道換了就換的值，換了就重新量
 */
export function MoreStatesButton({
  scrollerRef,
  layoutKey,
}: {
  scrollerRef: RefObject<HTMLDivElement | null>;
  layoutKey: string;
}) {
  const { t } = useTranslation();
  const [hidden, setHidden] = useState(0);

  useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    const measure = () => setHidden(measureHidden(scroller));
    measure();
    scroller.addEventListener('scroll', measure, { passive: true });
    const observer = new ResizeObserver(measure);
    observer.observe(scroller);
    if (scroller.firstElementChild) observer.observe(scroller.firstElementChild);
    return () => {
      scroller.removeEventListener('scroll', measure);
      observer.disconnect();
    };
  }, [scrollerRef, layoutKey]);

  if (hidden === 0) return null;
  return (
    <>
      {/* 右緣淡出：雛形是內容區邊距內側 56px、到 85% 全成底色（`ui/desk.css` 的 `.more-fade`）。
          這裡的看板貼齊內容區，所以再加上一段邊距寬，淡出段落在同一個位置，邊距那段全是底色。 */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute top-0 right-0 bottom-2 w-[calc(--spacing(14)+var(--spacing-gutter))] bg-linear-to-r from-bg/0 to-bg to-66%"
      />
      {/* 雛形的按鈕對齊看板區塊內距內側（`ui/desk.css:132`、`:194`），這裡同樣讓出一段邊距寬，右緣和「代開單」鈕、卡片對齊。 */}
      <button
        type="button"
        onClick={() => scrollerRef.current && scrollOneColumn(scrollerRef.current)}
        className="absolute top-0 right-gutter z-[3] flex min-h-touch items-center border-none bg-transparent p-1.5"
      >
        <span className="flex min-h-8 items-center gap-1 rounded-full bg-surface px-3 text-[13px] font-bold text-brand shadow-[0_2px_10px_rgba(0,0,0,0.12)]">
          {t('board.moreStates', { count: hidden })}
          <svg viewBox="0 0 24 24" aria-hidden="true" className="size-3 fill-none stroke-current stroke-2">
            <path d="M9 5l7 7-7 7" />
          </svg>
        </span>
      </button>
    </>
  );
}
