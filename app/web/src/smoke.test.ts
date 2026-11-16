import { expect, test } from 'vitest';

// 只確認單元測試這條線起得來，不測任何產品行為。
test('單元測試跑得起來', () => {
  expect(1 + 1).toBe(2);
});
