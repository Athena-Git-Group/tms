import { forwardRef, useImperativeHandle, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { DeskMenuKey } from '../../api/types';
import { MENU_ITEMS, quantityOf, type CreateRequestDraft, type MenuItem } from './create-request-logic';

const ITEM_ICONS: Record<DeskMenuKey, ReactNode> = {
  毛巾: <path d="M6 4h12v16l-3-2-3 2-3-2-3 2zM6 9h12" />,
  浴巾: <path d="M4 5h16v14H4zM4 10h16M8 15h8" />,
  礦泉水: <path d="M10 3h4v3l2 3v11a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V9l2-3z" />,
  拖鞋: <path d="M8 4a4 4 0 0 1 4 4v9a3 3 0 0 1-6 0V8a2 2 0 0 1 2-4zM6 11h6" />,
  牙刷組: <path d="M7 21V9M7 9V3h4v6zM15 21v-8a3 3 0 0 1 6 0v8z" />,
  嬰兒床: <path d="M4 8v12M20 8v12M4 12h16M4 18h16M8 12v6M12 12v6M16 12v6" />,
  加床: <path d="M3 18V7M21 18v-5H3M7 13V10h5v3M3 15h18" />,
  冰塊: <path d="M5 9l7-4 7 4v7l-7 4-7-4zM5 9l7 4 7-4M12 13v7" />,
  冷氣: <path d="M3 6h18v7H3zM6 10h12M8 17l-1 3M12 17v3M16 17l1 3" />,
  熱水: <path d="M12 3c3 4 5 7 5 10a5 5 0 0 1-10 0c0-3 2-6 5-10z" />,
  燈具: <path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z" />,
  電視: <path d="M3 6h18v12H3zM8 21h8M12 18v3" />,
};

const DEPT_STYLES = {
  housekeeping: { icon: 'bg-brand-container text-brand', dept: 'text-brand' },
  engineering: { icon: 'bg-warm-bg text-warm-fg', dept: 'text-amber' },
} as const;

export const ItemGrid = forwardRef<
  HTMLButtonElement,
  {
    draft: CreateRequestDraft;
    disabled: boolean;
    onAdd: (item: MenuItem) => void;
    onSubtract: (item: MenuItem) => void;
    onRemove: (item: MenuItem) => void;
  }
>(function ItemGrid({ draft, disabled, onAdd, onSubtract, onRemove }, firstTileRef) {
  const { t } = useTranslation();
  const tiles = useRef(new Map<DeskMenuKey, HTMLButtonElement>());
  useImperativeHandle(firstTileRef, () => tiles.current.get(MENU_ITEMS[0]!.key)!, []);
  const focusTileAfter = (item: MenuItem, change: () => void) => {
    change();
    requestAnimationFrame(() => tiles.current.get(item.key)?.focus());
  };

  return (
    <div className="grid grid-cols-4 gap-2">
      {MENU_ITEMS.map((item) => {
        const quantity = quantityOf(draft, item.key);
        const name = t(`createRequest.items.${item.labelKey}`);
        const styles = DEPT_STYLES[item.dept];
        return (
          <div
            key={item.key}
            className={`flex flex-col overflow-hidden rounded-2xl bg-surface ${quantity > 0 ? 'shadow-[0_0_0_2px_var(--color-brand)]' : ''}`}
          >
            <button
              ref={(tile) => {
                if (tile) tiles.current.set(item.key, tile);
                else tiles.current.delete(item.key);
              }}
              type="button"
              disabled={disabled}
              aria-label={
                quantity > 0
                  ? t('createRequest.addOneCurrent', { item: name, quantity })
                  : t('createRequest.addOne', { item: name })
              }
              onClick={() => onAdd(item)}
              className="flex min-h-item-tile w-full flex-col items-center justify-center gap-1 border-none bg-transparent px-1.5 pt-3 pb-2.5 text-center"
            >
              <span className={`relative flex size-11 items-center justify-center rounded-[14px] ${styles.icon}`}>
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  className="size-[26px] fill-none stroke-current stroke-[1.7]"
                >
                  {ITEM_ICONS[item.key]}
                </svg>
                {quantity > 0 && (
                  <span className="absolute -top-1.5 -right-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1.5 text-xs font-bold text-white motion-safe:animate-badge-pop">
                    {quantity}
                  </span>
                )}
              </span>
              <span className="text-sm leading-[1.25] font-semibold text-text">{name}</span>
              <span className={`text-[11px] font-bold ${styles.dept}`}>{t(`board.card.depts.${item.dept}`)}</span>
            </button>
            {quantity > 0 &&
              (item.max > 1 ? (
                <div className="flex min-h-touch items-stretch border-t-[0.5px] border-line">
                  <button
                    type="button"
                    disabled={disabled}
                    aria-label={t('createRequest.removeOne', { item: name })}
                    onClick={() => (quantity === 1 ? focusTileAfter(item, () => onSubtract(item)) : onSubtract(item))}
                    className="min-h-touch flex-1 border-none bg-transparent text-xl text-brand"
                  >
                    −
                  </button>
                  <span className="flex min-w-6 items-center justify-center text-[15px] font-bold">{quantity}</span>
                  <button
                    type="button"
                    disabled={disabled}
                    aria-label={t('createRequest.addOne', { item: name })}
                    onClick={() => onAdd(item)}
                    className="min-h-touch flex-1 border-none bg-transparent text-xl text-brand"
                  >
                    +
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  disabled={disabled}
                  aria-label={t('createRequest.removeItem', { item: name })}
                  onClick={() => focusTileAfter(item, () => onRemove(item))}
                  className="min-h-touch w-full border-0 border-t-[0.5px] border-solid border-line bg-transparent text-sm font-semibold text-brand"
                >
                  {t('createRequest.remove')}
                </button>
              ))}
          </div>
        );
      })}
    </div>
  );
});
