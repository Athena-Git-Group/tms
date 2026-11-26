import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { fetchPhotoBlob } from '../../api/repair-photos';
import type { DeskTaskDetail } from '../../api/types';
import { photoUrlTracker, type PhotoUrlTracker } from '../../lib/photo-urls';
import { repairPhotoRows, type PhotoRow, type PhotoRowKind } from '../../lib/repair-photo-rows';

type PhotoLoad = { kind: 'loading' } | { kind: 'loaded'; url: string } | { kind: 'failed' };

const LOADING: PhotoLoad = { kind: 'loading' };

interface ViewerTarget {
  row: PhotoRowKind;
  index: number;
}

export function RepairPhotoStrip({ task }: { task: DeskTaskDetail }) {
  const rows = repairPhotoRows(task);
  const loads = usePhotoLoads(rows.flatMap((row) => row.photoIds));
  const [viewer, setViewer] = useState<ViewerTarget | null>(null);
  const viewerRow = viewer && rows.find((row) => row.kind === viewer.row);

  if (rows.length === 0) return null;
  return (
    <>
      {rows.map((row) => (
        <PhotoRowSection
          key={row.kind}
          row={row}
          loads={loads.state}
          onFailed={loads.markFailed}
          onOpen={(index) => setViewer({ row: row.kind, index })}
        />
      ))}
      {viewer && viewerRow && viewer.index < viewerRow.photoIds.length && (
        <PhotoViewer
          row={viewerRow}
          index={viewer.index}
          loads={loads.state}
          onFailed={loads.markFailed}
          onMove={(index) => setViewer({ row: viewerRow.kind, index })}
          onClose={() => setViewer(null)}
        />
      )}
    </>
  );
}

function usePhotoLoads(ids: readonly string[]) {
  const [state, setState] = useState<ReadonlyMap<string, PhotoLoad>>(new Map());
  const trackerRef = useRef<PhotoUrlTracker | null>(null);
  const key = ids.join(',');

  useEffect(
    () => () => {
      trackerRef.current?.release();
      trackerRef.current = null;
    },
    [],
  );

  useEffect(() => {
    const tracker = (trackerRef.current ??= photoUrlTracker(
      (blob) => URL.createObjectURL(blob),
      (url) => URL.revokeObjectURL(url),
    ));
    const wanted = key === '' ? [] : key.split(',');
    const jobs = tracker.sync(wanted);
    setState((previous) => {
      const next = new Map<string, PhotoLoad>();
      for (const id of wanted) next.set(id, previous.get(id) ?? LOADING);
      for (const job of jobs) next.set(job.id, LOADING);
      return next;
    });
    const settle = (id: string, load: PhotoLoad) =>
      setState((previous) => (previous.has(id) ? new Map(previous).set(id, load) : previous));
    // React 開發模式會把 effect 跑兩次、中間先卸載；排到下一個 microtask 才送，第一次的請求在送出前就已中止，不會白白佔住連線。
    for (const { id, signal } of jobs) {
      queueMicrotask(() => {
        if (signal.aborted) return;
        fetchPhotoBlob(id, signal).then(
          (blob) => {
            const url = tracker.resolve(id, blob);
            if (url) settle(id, { kind: 'loaded', url });
          },
          () => {
            if (!signal.aborted) settle(id, { kind: 'failed' });
          },
        );
      });
    }
  }, [key]);

  const markFailed = (id: string) =>
    setState((previous) => (previous.has(id) ? new Map(previous).set(id, { kind: 'failed' }) : previous));
  return { state, markFailed };
}

const ROW_TITLE_KEYS = {
  report: 'detail.photos.report',
  completion: 'detail.photos.completion',
} as const satisfies Record<PhotoRowKind, string>;

function PhotoRowSection({
  row,
  loads,
  onFailed,
  onOpen,
}: {
  row: PhotoRow;
  loads: ReadonlyMap<string, PhotoLoad>;
  onFailed: (id: string) => void;
  onOpen: (index: number) => void;
}) {
  const { t } = useTranslation();
  const headingId = useId();
  const title = t(ROW_TITLE_KEYS[row.kind]);
  const total = row.photoIds.length;
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-1.5">
      <h3 id={headingId} className="m-0 px-4 text-[13px] font-normal text-text-2">
        {title}
      </h3>
      <ul className="m-0 grid list-none grid-cols-3 gap-2 rounded-card bg-surface p-3">
        {row.photoIds.map((id, index) => {
          const load = loads.get(id) ?? LOADING;
          return (
            <li key={id} className="min-w-0">
              <button
                type="button"
                aria-label={`${title} ${t('detail.photos.viewer.counter', { index: index + 1, total })}`}
                aria-busy={load.kind === 'loading'}
                onClick={() => onOpen(index)}
                className="flex aspect-square w-full min-h-touch min-w-touch items-center justify-center overflow-hidden rounded-xl border-none bg-[#E5E5EA] p-0"
              >
                <PhotoContent load={load} onError={() => onFailed(id)} textClass="px-1 text-[12px] leading-tight" />
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function PhotoContent({ load, onError, textClass }: { load: PhotoLoad; onError: () => void; textClass: string }) {
  const { t } = useTranslation();
  switch (load.kind) {
    case 'loading':
      return null;
    case 'loaded':
      return <img src={load.url} alt="" onError={onError} className="size-full object-cover" />;
    case 'failed':
      return <span className={`text-center text-text-2 ${textClass}`}>{t('detail.photos.unavailable')}</span>;
  }
}

function PhotoViewer({
  row,
  index,
  loads,
  onFailed,
  onMove,
  onClose,
}: {
  row: PhotoRow;
  index: number;
  loads: ReadonlyMap<string, PhotoLoad>;
  onFailed: (id: string) => void;
  onMove: (index: number) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const titleId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);
  const total = row.photoIds.length;
  const id = row.photoIds[index]!;
  const load = loads.get(id) ?? LOADING;

  useEffect(() => closeRef.current?.focus(), []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const navButton =
    'min-h-touch min-w-[96px] rounded-full border-[1.5px] border-solid border-white/70 bg-transparent px-4 text-[17px] font-semibold text-white disabled:opacity-35';
  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className="fixed inset-0 z-50 flex flex-col bg-black/85 text-white"
    >
      <div className="grid min-h-14 grid-cols-[1fr_auto_1fr] items-center px-2">
        <span>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            className="inline-flex min-h-touch items-center border-none bg-transparent px-2 text-[17px] text-white"
          >
            {t('detail.photos.viewer.close')}
          </button>
        </span>
        <h2 id={titleId} className="m-0 text-[17px] font-semibold">
          {t(ROW_TITLE_KEYS[row.kind])}
        </h2>
        <span className="text-right pr-2 text-[15px] text-white/80">
          {t('detail.photos.viewer.counter', { index: index + 1, total })}
        </span>
      </div>
      <div className="flex min-h-0 flex-auto items-center justify-center p-4">
        {load.kind === 'loaded' ? (
          <img src={load.url} alt="" onError={() => onFailed(id)} className="size-full object-contain" />
        ) : (
          <div className="flex aspect-square w-[min(60vw,60vh)] items-center justify-center rounded-2xl bg-[#3A3A3C]">
            {load.kind === 'failed' && <span className="text-[17px] text-white/80">{t('detail.photos.unavailable')}</span>}
          </div>
        )}
      </div>
      <div className="flex justify-center gap-4 px-4 pt-2 pb-6">
        <button type="button" className={navButton} disabled={index === 0} onClick={() => onMove(index - 1)}>
          {t('detail.photos.viewer.previous')}
        </button>
        <button type="button" className={navButton} disabled={index === total - 1} onClick={() => onMove(index + 1)}>
          {t('detail.photos.viewer.next')}
        </button>
      </div>
    </div>,
    document.body,
  );
}
