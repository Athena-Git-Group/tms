export const MAX_QUANTITY = 5;

export type Quantities = Readonly<Record<string, number>>;

export type QuantityAction =
  | { type: 'increment'; key: string }
  | { type: 'decrement'; key: string }
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

export function chosenCount(quantities: Quantities): number {
  return Object.keys(quantities).length;
}

export type SubmitLabelKey = 'guest.supply.choose' | 'guest.supply.sendOne' | 'guest.supply.sendMany';

export function submitLabel(chosen: number): SubmitLabelKey {
  if (chosen === 0) return 'guest.supply.choose';
  if (chosen === 1) return 'guest.supply.sendOne';
  return 'guest.supply.sendMany';
}
