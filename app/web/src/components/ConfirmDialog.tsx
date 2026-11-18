import { useId } from 'react';

/**
 * 要先問一次才做的事用的確認對話框：左邊「取消」、右邊做這件事的按鈕，蓋在整個畫面上、置中。
 * 打開時焦點落在右邊那一顆；按 Esc 等於取消。外觀和只有「好」的 `AlertDialog` 同一套。
 */
export function ConfirmDialog({
  title,
  body,
  cancelLabel,
  confirmLabel,
  onCancel,
  onConfirm,
}: {
  title: string;
  body: string;
  cancelLabel: string;
  confirmLabel: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const titleId = useId();
  const bodyId = useId();

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/20"
      onKeyDown={(event) => {
        if (event.key === 'Escape') onCancel();
      }}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={bodyId}
        className="flex w-dialog flex-col overflow-hidden rounded-card bg-[rgba(250,250,250,.98)] shadow-[0_20px_50px_rgba(0,0,0,.25)]"
      >
        <div className="px-[18px] pt-5 pb-4 text-center">
          <h2 id={titleId} className="m-0 text-[17px] font-semibold">
            {title}
          </h2>
          <p id={bodyId} className="m-0 mt-1 text-sm leading-[1.4]">
            {body}
          </p>
        </div>
        <div className="flex border-t-[0.5px] border-[rgba(60,60,67,.29)]">
          <button
            type="button"
            className="min-h-12 flex-1 border-none bg-transparent text-[17px] text-brand"
            onClick={onCancel}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            autoFocus
            className="min-h-12 flex-1 border-y-0 border-r-0 border-l-[0.5px] border-[rgba(60,60,67,.29)] bg-transparent text-[17px] font-semibold text-danger"
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
