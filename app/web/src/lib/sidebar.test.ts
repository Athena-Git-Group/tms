import { describe, expect, it } from 'vitest';
import { sidebarOpen } from './sidebar';

describe('sidebarOpen', () => {
  it('還沒選過時，總覽看板預設收起', () => {
    expect(sidebarOpen('board', null)).toBe(false);
  });

  it('還沒選過時，待派單頁預設打開', () => {
    expect(sidebarOpen('triage', null)).toBe(true);
  });

  it('自己選過打開，兩頁都打開', () => {
    expect(sidebarOpen('board', true)).toBe(true);
    expect(sidebarOpen('triage', true)).toBe(true);
  });

  it('自己選過收起，兩頁都收起', () => {
    expect(sidebarOpen('board', false)).toBe(false);
    expect(sidebarOpen('triage', false)).toBe(false);
  });

  it('不認得的頁面預設收起，選過就照選擇', () => {
    expect(sidebarOpen('unknown', null)).toBe(false);
    expect(sidebarOpen('unknown', true)).toBe(true);
  });
});
