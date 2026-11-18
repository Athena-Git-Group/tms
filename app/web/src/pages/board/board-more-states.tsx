import { useLayoutEffect, useState, type RefObject } from 'react';
import { useTranslation } from 'react-i18next';
import { columnsHiddenOnRight } from '../../lib/board-layout';

function headsIn(scroller: HTMLElement): HTMLElement[] {
  return [...scroller.querySelectorAll<HTMLElement>('[data-board-head]')];
}

function measureHidden(scroller: HTMLElement): number {
  const box = scroller.getBoundingClientRect();
  const visibleRight = box.left + scroller.clientLeft + scroller.clientWidth;
  return columnsHiddenOnRight(
    headsIn(scroller).map((head) => head.getBoundingClientRect().right),
    visibleRight,
  );
}

function scrollOneColumn(scroller: HTMLElement) {
  const [first, second] = headsIn(scroller);
  if (!first) return;
  const gap = first.parentElement ? parseFloat(getComputedStyle(first.parentElement).columnGap) || 0 : 0;
  const step = second
    ? second.getBoundingClientRect().left - first.getBoundingClientRect().left
    : first.getBoundingClientRect().width + gap;
  scroller.scrollBy({ left: step, behavior: 'smooth' });
}

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
      <div
        aria-hidden="true"
        className="pointer-events-none absolute top-0 right-0 bottom-2 w-[calc(--spacing(14)+var(--spacing-gutter))] bg-linear-to-r from-bg/0 to-bg to-66%"
      />
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
