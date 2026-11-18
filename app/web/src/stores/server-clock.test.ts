import { beforeEach, describe, expect, it } from 'vitest';
import { useServerClockStore } from './server-clock';

describe('useServerClockStore.syncWith', () => {
  beforeEach(() => {
    useServerClockStore.setState({ offsetMs: null, nowMs: null, serverUtcOffsetMinutes: 0 });
  });

  it('從 serverTime 尾端記下伺服器的時區偏移', () => {
    useServerClockStore.getState().syncWith('2026-10-26T14:10:00+08:00', new Date());
    expect(useServerClockStore.getState().serverUtcOffsetMinutes).toBe(480);
  });

  it('讀不出偏移時沿用上一次的值', () => {
    useServerClockStore.getState().syncWith('2026-10-26T14:10:00+08:00', new Date());
    useServerClockStore.getState().syncWith('2026-10-26T14:11:00', new Date());
    expect(useServerClockStore.getState().serverUtcOffsetMinutes).toBe(480);
  });
});
