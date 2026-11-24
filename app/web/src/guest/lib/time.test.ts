import { describe, expect, it } from 'vitest';
import { hhmm } from './time';

describe('hhmm', () => {
  it('24 小時制、補零、沒有日期與秒', () => {
    expect(hhmm(new Date(2026, 9, 26, 14, 5, 59).toISOString())).toBe('14:05');
    expect(hhmm(new Date(2026, 9, 26, 9, 0).toISOString())).toBe('09:00');
    expect(hhmm(new Date(2026, 9, 26, 0, 7).toISOString())).toBe('00:07');
  });
});
