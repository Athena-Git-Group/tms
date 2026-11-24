import { type KeyboardEvent, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { GUEST_OVERLAY_ID } from '../GuestShell';
import { buttonClass } from './button-class';

const ACTION = 'min-w-0 flex-1 basis-0';

export function CancelDialog({ title, onKeep, onConfirm }: { title: string; onKeep: () => void; onConfirm: () => void }) {
  const { t } = useTranslation();
  const keep = useRef<HTMLButtonElement>(null);
  const confirm = useRef<HTMLButtonElement>(null);

  useEffect(() => keep.current?.focus(), []);

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onKeep();
      return;
    }
    if (event.key !== 'Tab') return;
    event.preventDefault();
    (document.activeElement === keep.current ? confirm : keep).current?.focus();
  };

  const host = document.getElementById(GUEST_OVERLAY_ID);
  if (!host) return null;
  return createPortal(
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-guest-scrim px-4"
      onClick={(event) => event.target === event.currentTarget && onKeep()}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="guest-cancel-title"
        aria-describedby="guest-cancel-body"
        onKeyDown={onKeyDown}
        className="flex w-full max-w-[320px] flex-col gap-1.5 rounded-guest-lg bg-guest-surface-raised px-5 pt-[22px] pb-4 text-guest-text shadow-guest-popover motion-safe:animate-guest-pop"
      >
        <h2 id="guest-cancel-title" className="m-0 text-[20px] leading-[26px] font-semibold tracking-[.38px] text-balance">
          {t('guest.cancel.title')}
        </h2>
        <p id="guest-cancel-body" className="m-0 text-guest-callout tracking-[-.23px] [overflow-wrap:anywhere]">
          {t('guest.cancel.body', { title })}
        </p>
        <div className="mt-3.5 flex gap-2.5">
          <button ref={keep} type="button" className={`${buttonClass('gray', { tight: true })} ${ACTION}`} onClick={onKeep}>
            {t('guest.cancel.keep')}
          </button>
          <button ref={confirm} type="button" className={`${buttonClass('grayDanger', { tight: true })} ${ACTION}`} onClick={onConfirm}>
            {t('guest.cancel.confirm')}
          </button>
        </div>
      </div>
    </div>,
    host,
  );
}
