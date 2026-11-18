import { describe, expect, it } from 'vitest';
import { boardColumnWidth, boardContentWidth, columnsHiddenOnRight } from './board-layout';

const rightOf = (i: number) => 16 + i * 210 + 200;
const eightRights = Array.from({ length: 8 }, (_, i) => rightOf(i));

describe('columnsHiddenOnRight', () => {
  it.each([
    ['1280 寬、側欄收起', 1280, 2],
    ['1280 寬、側欄打開', 980, 4],
    ['1180 寬、側欄收起', 1180, 3],
    ['1180 寬、側欄打開', 880, 4],
  ])('%s時照畫面規劃的欄數表', (_, visibleRight, hidden) => {
    expect(columnsHiddenOnRight(eightRights, visibleRight)).toBe(hidden);
  });

  it('只露出一部分的欄算看不到，差不到 1px 的捨入誤差仍算看得到', () => {
    expect(columnsHiddenOnRight([1266], 1265)).toBe(0);
    expect(columnsHiddenOnRight([1266], 1264)).toBe(1);
  });

  it('全部都看得到時是 0', () => {
    expect(columnsHiddenOnRight(eightRights, 1920)).toBe(0);
  });
});

describe('boardContentWidth', () => {
  it('8 欄至少撐到 8 個欄寬、7 個欄距加左右邊距', () => {
    expect(boardContentWidth(8)).toBe(
      'max(100%, calc(8 * var(--min-width-board-column) + 7 * var(--gap-board) + 2 * var(--spacing-gutter)))',
    );
  });

  it('只剩 1 欄時沒有欄距', () => {
    expect(boardContentWidth(1)).toBe(
      'max(100%, calc(1 * var(--min-width-board-column) + 0 * var(--gap-board) + 2 * var(--spacing-gutter)))',
    );
  });
});

describe('boardColumnWidth', () => {
  it('8 欄都在時沒有上限', () => {
    expect(boardColumnWidth(8)).toBe('min-w-board-column');
  });

  it('篩選後欄數變少時有最寬寬度', () => {
    expect(boardColumnWidth(2)).toBe('min-w-board-column max-w-board-column-filtered');
  });
});
