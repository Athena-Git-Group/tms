import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { AlertDialog } from '../../components/AlertDialog';
import type { DispatchNotice } from '../../lib/dispatch';
import { useDispatchStore } from '../../stores/dispatch';
import { ConflictDialog } from './ConflictDialog';
import { useUndoAssign } from './use-dispatch';

export function AssignToastBar() {
  const { t } = useTranslation();
  const toast = useDispatchStore((state) => state.toast);
  const undo = useUndoAssign();
  if (!toast) return null;
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-7 z-30 flex justify-center">
      <div
        key={toast.taskId}
        role="status"
        className="pointer-events-auto flex w-[min(420px,calc(100%-64px))] animate-rise-in items-center gap-2.5 rounded-2xl bg-[rgba(30,30,30,.92)] py-2 pr-2 pl-4 text-[15px] text-white shadow-[0_8px_24px_rgba(0,0,0,.2)]"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true" className="size-[18px] shrink-0 fill-none stroke-current stroke-[2.4]">
          <path d="M5 12.5l4.5 4.5L19 7.5" />
        </svg>
        <span className="grow">
          {t('dispatch.assigned', { dept: t(`board.card.depts.${toast.dept}`), roomNo: toast.roomNo })}
        </span>
        <button
          type="button"
          onClick={() => void undo(toast)}
          className="min-h-touch shrink-0 rounded-control border-none bg-transparent px-3 text-[15px] font-bold text-[#7FD3DB]"
        >
          {t('dispatch.undo')}
        </button>
      </div>
    </div>
  );
}

function noticeText(
  notice: Exclude<DispatchNotice, { kind: 'assignConflict' }>,
  t: TFunction,
): { title: string; body: string } {
  switch (notice.kind) {
    case 'undoClaimed':
      return {
        title: t('dispatch.undoFailed.title'),
        body: t('dispatch.undoFailed.claimed', {
          name: notice.name,
          roomNo: notice.roomNo,
          dept: t(`board.card.depts.${notice.dept}`),
        }),
      };
    case 'undoGuestWithdrew':
      return {
        title: t('dispatch.undoFailed.title'),
        body: t('dispatch.undoFailed.guestWithdrew', { roomNo: notice.roomNo }),
      };
    case 'undoNotSent':
      return {
        title: t('dispatch.undoNotSent.title'),
        body: t('dispatch.undoNotSent.body', { roomNo: notice.roomNo, dept: t(`board.card.depts.${notice.dept}`) }),
      };
    case 'assignNotSent':
      return {
        title: t('dispatch.assignNotSent.title'),
        body: t('dispatch.assignNotSent.body', { roomNo: notice.roomNo }),
      };
  }
}

export function DispatchNoticeDialog() {
  const { t } = useTranslation();
  const notice = useDispatchStore((state) => state.notice);
  const closeNotice = useDispatchStore((state) => state.closeNotice);
  if (!notice) return null;
  if (notice.kind === 'assignConflict') return <ConflictDialog notice={notice} action="assign" onClose={closeNotice} />;
  const { title, body } = noticeText(notice, t);
  return <AlertDialog title={title} body={body} onClose={closeNotice} />;
}
