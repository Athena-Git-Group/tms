import { useQuery } from '@tanstack/react-query';
import { useEffect, useId, useRef, type KeyboardEvent } from 'react';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { listDeskRooms } from '../../api/desk';
import { REREAD_INTERVAL_MS } from '../../api/query-client';
import { AlertDialog } from '../../components/AlertDialog';
import { CircleCloseButton } from '../../components/CircleCloseButton';
import {
  canSubmit,
  hasOtherText,
  roomStatus,
  summaryLists,
  ticketCount,
  type CreateRequestDraft,
  type MenuItem,
} from './create-request-logic';
import { ItemGrid } from './ItemGrid';
import { OtherRequest } from './OtherRequest';
import { RoomSection, type RoomListState } from './RoomSection';
import { SimilarTasksNotice } from './SimilarTasksNotice';
import { useCreateRequest, type NotSentNotice } from './use-create-request';

const ROOMS_GC_MS = 5 * 60_000;

const deskRoomsQuery = {
  queryKey: ['desk-rooms'],
  queryFn: ({ signal }: { signal: AbortSignal }) => listDeskRooms(signal),
  retry: false,
  gcTime: ROOMS_GC_MS,
} as const;

const FOCUSABLE = 'button:not(:disabled), input:not([readonly]), textarea:not([readonly]), [tabindex="0"]';

export function CreateRequestPanel({ initialRoomNo, onCancel }: { initialRoomNo: string; onCancel: () => void }) {
  const { t } = useTranslation();
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const roomInputRef = useRef<HTMLInputElement>(null);
  const firstTileRef = useRef<HTMLButtonElement>(null);
  const submitRef = useRef<HTMLButtonElement>(null);
  const request = useCreateRequest(initialRoomNo);
  const { draft } = request;
  const { submitting } = request;

  const rooms = useQuery({
    ...deskRoomsQuery,
    refetchInterval: (query) => (query.state.status === 'error' ? REREAD_INTERVAL_MS : false),
  });
  const roomList: RoomListState = {
    rooms: rooms.data,
    loading: rooms.isPending && !rooms.isError,
    failed: rooms.isError,
    retry: () => void rooms.refetch(),
  };
  const status = roomStatus(draft.roomNo, rooms.data, rooms.isError);
  const count = ticketCount(draft);

  const prefilled = initialRoomNo !== '';
  useEffect(() => {
    if (window.matchMedia('(pointer: coarse)').matches) dialogRef.current?.focus();
    else if (prefilled) firstTileRef.current?.focus();
    else roomInputRef.current?.focus();
  }, [prefilled]);

  const close = () => {
    if (!submitting) onCancel();
  };

  const keepFocusInside = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'Tab' || !dialogRef.current) return;
    const focusable = [...dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)];
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!first || !last) return;
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const pickRoom = (roomNo: string) => {
    request.setRoomNo(roomNo);
    requestAnimationFrame(() => firstTileRef.current?.focus());
  };

  const closeNotice = () => {
    request.closeNotice();
    requestAnimationFrame(() => submitRef.current?.focus());
  };

  return (
    <>
      <div
        className="fixed inset-0 z-30 flex items-center justify-center"
        onKeyDown={(event) => {
          if (event.key === 'Escape') close();
        }}
      >
        <button
          type="button"
          tabIndex={-1}
          aria-label={t('createRequest.close')}
          onClick={close}
          className="absolute inset-0 border-none bg-black/[.32] p-0"
        />
        <div
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          tabIndex={-1}
          onKeyDown={keepFocusInside}
          className="relative flex h-create-request-panel w-create-request-panel flex-col overflow-hidden rounded-sheet bg-bg shadow-[0_20px_60px_rgba(0,0,0,.25)] outline-none"
        >
          <div className="grid min-h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center px-4 pt-2">
            <span>
              <CircleCloseButton label={t('createRequest.cancel')} onClick={close} disabled={submitting} />
            </span>
            <h2 id={titleId} className="m-0 text-center text-[17px] font-semibold whitespace-nowrap">
              {t('shell.createRequest')}
            </h2>
            <span />
          </div>
          <div className="flex min-h-0 flex-auto flex-col gap-3.5 overflow-y-auto px-5 pt-1 pb-4 *:shrink-0">
            <RoomSection
              ref={roomInputRef}
              roomNo={draft.roomNo}
              status={status}
              roomList={roomList}
              disabled={submitting}
              onRoomNoChange={request.setRoomNo}
              onPick={pickRoom}
            />
            <section className="flex flex-col gap-2">
              <span className="px-1 text-[13px] leading-[1.45] text-text-2">{t('createRequest.itemsTitle')}</span>
              <ItemGrid
                ref={firstTileRef}
                draft={draft}
                disabled={submitting}
                onAdd={request.add}
                onSubtract={request.subtract}
                onRemove={request.remove}
              />
            </section>
            <OtherRequest
              text={draft.otherText}
              dept={draft.otherDept}
              showDept={hasOtherText(draft)}
              disabled={submitting}
              onTextChange={request.setOtherText}
              onDeptChange={request.setOtherDept}
            />
            <SimilarTasksNotice draft={draft} />
          </div>
          <div className="flex shrink-0 flex-col gap-2.5 px-5 pt-3 pb-5">
            <Summary draft={draft} />
            <button
              ref={submitRef}
              type="button"
              disabled={!canSubmit(draft, status) || submitting}
              aria-busy={submitting}
              onClick={request.submit}
              className={`flex min-h-[54px] w-full items-center justify-center gap-2 rounded-full border-none text-[17px] font-semibold text-white ${
                submitting
                  ? 'bg-brand opacity-60'
                  : 'bg-brand shadow-[0_6px_16px_rgba(12,118,128,.3)] disabled:bg-line disabled:shadow-none'
              }`}
            >
              {submitting && (
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  className="size-4 animate-spin fill-none stroke-current stroke-[2.6]"
                >
                  <path d="M12 3a9 9 0 1 0 9 9" />
                </svg>
              )}
              {submitLabel(t, count)}
            </button>
          </div>
        </div>
      </div>
      {request.notice && <NotSentDialog notice={request.notice} onClose={closeNotice} />}
    </>
  );
}

function submitLabel(t: TFunction, count: number): string {
  if (count === 0) return t('createRequest.submit');
  return count === 1 ? t('createRequest.submitOne') : t('createRequest.submitCount', { n: count });
}

function NotSentDialog({ notice, onClose }: { notice: NotSentNotice; onClose: () => void }) {
  const { t } = useTranslation();
  const values = { roomNo: notice.roomNo, n: notice.count };
  const body = {
    room_not_found: t('createRequest.notSent.roomNotFound', values),
    forbidden: t('createRequest.notSent.forbidden', values),
    invalid: t('createRequest.notSent.invalid', values),
    not_sent: t('createRequest.notSent.failed', values),
  }[notice.kind];
  return <AlertDialog title={t('createRequest.notSent.title')} body={body} onClose={onClose} />;
}

function Summary({ draft }: { draft: CreateRequestDraft }) {
  const { t } = useTranslation();
  const lists = summaryLists(draft, {
    itemName: (item: MenuItem) => t(`createRequest.items.${item.labelKey}`),
    other: t('createRequest.otherInSummary'),
    listSeparator: t('createRequest.summary.listSeparator'),
  });
  const segments = lists && [
    lists.housekeeping && t('createRequest.summary.housekeeping', { list: lists.housekeeping }),
    lists.engineering && t('createRequest.summary.engineering', { list: lists.engineering }),
  ];
  return (
    <p aria-live="polite" className="m-0 px-1 text-[13px] leading-[1.45] text-text-2 empty:hidden">
      {segments &&
        t(ticketCount(draft) === 1 ? 'createRequest.summary.textOne' : 'createRequest.summary.text', {
          n: ticketCount(draft),
          segments: segments.filter(Boolean).join(t('createRequest.summary.segmentSeparator')),
        })}
    </p>
  );
}
