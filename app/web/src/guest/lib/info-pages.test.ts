import { describe, expect, it } from 'vitest';
import { listableInfoPages } from './info-pages';

describe('listableInfoPages', () => {
  it('照後端的順序，標題是空的、只有空白或沒有標題的不列', () => {
    const pages = [
      { key: 'breakfast', title: '早餐', body: '一樓餐廳' },
      { key: 'untitled', title: '', body: '這一頁沒有標題' },
      { key: 'blank', title: '   ', body: '只有空白' },
      { key: 'missing', title: null, body: '沒有標題' },
      { key: 'pool', title: '游泳池', body: null },
    ];
    expect(listableInfoPages(pages).map((page) => page.key)).toEqual(['breakfast', 'pool']);
  });

  it('一頁都沒有時是空的', () => {
    expect(listableInfoPages([])).toEqual([]);
  });
});
