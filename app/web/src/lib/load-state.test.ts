import { describe, expect, it } from 'vitest';
import { loadStateOf } from './load-state';

describe('loadStateOf', () => {
  it('從來沒讀到過也沒失敗過是載入中', () => {
    expect(loadStateOf({ data: undefined, isError: false, errorUpdatedAt: 0 })).toBe('loading');
  });

  it('第一次就讀不到是讀取失敗', () => {
    expect(loadStateOf({ data: undefined, isError: true, errorUpdatedAt: 1000 })).toBe('failed');
  });

  it('失敗過一次後重讀中（失敗狀態暫時清掉）仍是讀取失敗，不閃回骨架', () => {
    expect(loadStateOf({ data: undefined, isError: false, errorUpdatedAt: 1000 })).toBe('failed');
  });

  it('有資料就是已讀到', () => {
    expect(loadStateOf({ data: { tasks: [] }, isError: false, errorUpdatedAt: 0 })).toBe('ready');
  });

  it('有資料之後重讀失敗，畫面仍留著舊資料', () => {
    expect(loadStateOf({ data: { tasks: [] }, isError: true, errorUpdatedAt: 2000 })).toBe('ready');
  });
});
