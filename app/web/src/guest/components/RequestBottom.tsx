import { forwardRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { GuestTask } from '../api/types';
import { stageOf } from '../lib/stage';
import { buttonClass } from './button-class';

const NOTE = 'm-0 px-4 text-center text-guest-footnote tracking-[-.08px] text-guest-text-secondary';

export const RequestBottom = forwardRef<
  HTMLButtonElement,
  { task: GuestTask; cancelling: boolean; onCancel: () => void }
>(function RequestBottom({ task, cancelling, onCancel }, cancelButton) {
  const { t } = useTranslation();
  const stage = stageOf(task);
  if (stage.cancellable) {
    return (
      <div className="flex flex-col gap-2">
        <button
          ref={cancelButton}
          type="button"
          className={buttonClass('grayDanger', { block: true })}
          disabled={cancelling}
          aria-busy={cancelling || undefined}
          onClick={onCancel}
        >
          {t('guest.request.cancel')}
        </button>
        <p className={NOTE}>{t('guest.request.cancelNote')}</p>
      </div>
    );
  }
  return stage.locked ? <p className={NOTE}>{t('guest.request.locked')}</p> : null;
});
