/** 每一項最多 5。 */
export const MAX_QUANTITY = 5;

/** 服務項目的鍵對到選的數量；沒出現的鍵就是 0。 */
export type Quantities = Readonly<Record<string, number>>;

export type QuantityAction =
  | { type: 'increment'; key: string }
  | { type: 'decrement'; key: string }
  /** 不給鍵就是全部歸零；給鍵只歸零那一項（送出成功的那幾項）。 */
  | { type: 'reset'; key?: string };

export const NO_QUANTITIES: Quantities = {};

export function quantityOf(quantities: Quantities, key: string): number {
  return quantities[key] ?? 0;
}

export function quantitiesReducer(state: Quantities, action: QuantityAction): Quantities {
  switch (action.type) {
    case 'increment':
      return withQuantity(state, action.key, Math.min(MAX_QUANTITY, quantityOf(state, action.key) + 1));
    case 'decrement':
      return withQuantity(state, action.key, Math.max(0, quantityOf(state, action.key) - 1));
    case 'reset':
      return action.key === undefined ? NO_QUANTITIES : withQuantity(state, action.key, 0);
  }
}

function withQuantity(state: Quantities, key: string, quantity: number): Quantities {
  if (quantityOf(state, key) === quantity) return state;
  const { [key]: _dropped, ...rest } = state;
  return quantity === 0 ? rest : { ...rest, [key]: quantity };
}

/** 選了幾項（數量大於 0 的項目數，不是總件數）。 */
export function chosenCount(quantities: Quantities): number {
  return Object.keys(quantities).length;
}

/** 頁底按鈕的字：一項都沒選「選擇數量」，一項「送出 1 項」，其餘「送出 {n} 項」。 */
export type SubmitLabelKey = 'guest.supply.choose' | 'guest.supply.sendOne' | 'guest.supply.sendMany';

export function submitLabel(chosen: number): SubmitLabelKey {
  if (chosen === 0) return 'guest.supply.choose';
  if (chosen === 1) return 'guest.supply.sendOne';
  return 'guest.supply.sendMany';
}
