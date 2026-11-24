import { forwardRef, useId } from 'react';
import { useTranslation } from 'react-i18next';
import type { DeskRoom } from '../../api/types';
import { SkeletonBlock } from '../../components/SkeletonBlock';
import {
  ROOM_NO_MAX_LENGTH,
  avatarOf,
  monthDay,
  roomFeedback,
  type RoomFeedback,
  type RoomStatus,
} from './create-request-logic';

export interface RoomListState {
  rooms: DeskRoom[] | undefined;
  loading: boolean;
  failed: boolean;
  retry: () => void;
}

export const RoomSection = forwardRef<
  HTMLInputElement,
  {
    roomNo: string;
    status: RoomStatus;
    roomList: RoomListState;
    disabled: boolean;
    onRoomNoChange: (raw: string) => void;
    onPick: (roomNo: string) => void;
  }
>(function RoomSection({ roomNo, status, roomList, disabled, onRoomNoChange, onPick }, ref) {
  const { t } = useTranslation();
  const inputId = useId();

  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex min-h-14 items-center gap-3 rounded-card bg-surface px-4 focus-within:shadow-[0_0_0_3px_var(--color-brand)]">
        <label htmlFor={inputId} className="shrink-0 text-[17px] font-semibold">
          {t('createRequest.roomLabel')}
        </label>
        <input
          ref={ref}
          id={inputId}
          type="text"
          inputMode="numeric"
          maxLength={ROOM_NO_MAX_LENGTH}
          autoComplete="off"
          readOnly={disabled}
          placeholder={t('createRequest.roomPlaceholder')}
          value={roomNo}
          onChange={(event) => onRoomNoChange(event.target.value)}
          className="min-w-0 flex-auto border-none bg-transparent py-2 text-[22px] font-bold tracking-[1px] text-text outline-none placeholder:text-[17px] placeholder:font-normal placeholder:tracking-normal placeholder:text-text-4"
        />
      </div>
      <RoomFeedbackArea
        feedback={roomFeedback(roomNo, status, roomList)}
        disabled={disabled}
        onRetry={roomList.retry}
        onPick={onPick}
      />
    </div>
  );
});

function RoomFeedbackArea({
  feedback,
  disabled,
  onRetry,
  onPick,
}: {
  feedback: RoomFeedback;
  disabled: boolean;
  onRetry: () => void;
  onPick: (roomNo: string) => void;
}) {
  const { t } = useTranslation();

  switch (feedback.kind) {
    case 'guest':
      return <GuestCard room={feedback.room} />;
    case 'vacant':
      return (
        <p className="m-0 rounded-card bg-surface px-4 py-3 text-sm leading-[1.45] text-text-2 motion-safe:animate-drop-in">
          {t('createRequest.vacant', { roomNo: feedback.roomNo })}
        </p>
      );
    case 'loading':
      return (
        <div aria-busy="true" className="flex flex-wrap gap-2">
          {[0, 1, 2].map((index) => (
            <SkeletonBlock key={index} className="h-touch w-28 rounded-xl" />
          ))}
        </div>
      );
    case 'listFailed':
      return (
        <div className="flex items-center gap-3 px-1">
          <span className="flex-auto text-[13px] leading-[1.45] text-text-2">{t('createRequest.roomsFailed')}</span>
          <button
            type="button"
            onClick={onRetry}
            className="min-h-touch shrink-0 rounded-full border border-solid border-brand bg-transparent px-4 text-[15px] font-semibold text-brand"
          >
            {t('createRequest.retry')}
          </button>
        </div>
      );
    case 'suggestions':
      return (
        <div className="flex flex-wrap gap-2">
          {feedback.rooms.map((room) => (
            <button
              key={room.roomNo}
              type="button"
              disabled={disabled}
              onClick={() => onPick(room.roomNo)}
              className="inline-flex min-h-touch items-center gap-1.5 rounded-xl border-none bg-surface px-3.5 text-[15px]"
            >
              <b className="font-bold">{room.roomNo}</b>
              <span className="text-text-2">{room.guest?.name ?? ''}</span>
            </button>
          ))}
        </div>
      );
    case 'none':
      return null;
  }
}

function GuestCard({ room }: { room: DeskRoom }) {
  const { t } = useTranslation();
  const guest = room.guest;
  if (!guest) return null;
  const name = guest.name ?? '';
  return (
    <div className="flex items-center gap-3 rounded-card bg-brand-container motion-safe:animate-drop-in px-4 py-3">
      <span
        aria-hidden="true"
        className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand text-[17px] font-bold text-white"
      >
        {avatarOf(name)}
      </span>
      <span className="flex min-w-0 flex-auto flex-col">
        <span className="text-base font-bold [overflow-wrap:anywhere]">{name}</span>
        <span className="text-[13px] text-brand-on-container">
          {`${room.roomNo} · ${monthDay(guest.checkinDate)}–${monthDay(guest.checkoutDate)}`}
        </span>
      </span>
      {guest.vip && (
        <span className="shrink-0 rounded-md bg-warm-bg px-1.5 py-px text-[11px] font-extrabold text-warm-fg">
          {t('board.card.vip')}
        </span>
      )}
    </div>
  );
}
