import { describe, expect, it } from 'vitest';
import { MAX_QUANTITY, NO_QUANTITIES, chosenCount, quantitiesReducer, quantityOf, submitLabel } from './supply-quantities';

const press = (times: number, type: 'increment' | 'decrement', key: string, from = NO_QUANTITIES) =>
  Array.from({ length: times }).reduce<typeof NO_QUANTITIES>((state) => quantitiesReducer(state, { type, key }), from);

describe('quantitiesReducer', () => {
  it('加號一次加 1', () => {
    expect(quantityOf(press(2, 'increment', '礦泉水'), '礦泉水')).toBe(2);
  });

  it(`到 ${MAX_QUANTITY} 就停住`, () => {
    expect(quantityOf(press(MAX_QUANTITY + 2, 'increment', '礦泉水'), '礦泉水')).toBe(MAX_QUANTITY);
  });

  it('減到 0 就停住，0 的項目不算選了', () => {
    const state = press(3, 'decrement', '礦泉水', press(1, 'increment', '礦泉水'));
    expect(quantityOf(state, '礦泉水')).toBe(0);
    expect(chosenCount(state)).toBe(0);
  });

  it('歸零可以只歸一項，也可以全部', () => {
    const state = press(2, 'increment', '毛巾', press(1, 'increment', '衣架'));
    expect(quantitiesReducer(state, { type: 'reset', key: '衣架' })).toEqual({ 毛巾: 2 });
    expect(quantitiesReducer(state, { type: 'reset' })).toEqual({});
  });
});

describe('submitLabel', () => {
  it.each([
    [0, 'guest.supply.choose'],
    [1, 'guest.supply.sendOne'],
    [3, 'guest.supply.sendMany'],
  ])('選了 %i 項', (chosen, label) => {
    expect(submitLabel(chosen)).toBe(label);
  });
});
