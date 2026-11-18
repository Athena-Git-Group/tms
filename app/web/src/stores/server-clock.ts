import { useEffect } from 'react';
import { create } from 'zustand';
import { clockOffsetMs, correctedNow, utcOffsetMinutes } from '../lib/server-clock';

interface ServerClockState {
  offsetMs: number | null;
  nowMs: number | null;
  serverUtcOffsetMinutes: number;
  syncWith: (serverTime: string, receivedAt: Date) => void;
  tick: () => void;
}

export const useServerClockStore = create<ServerClockState>((set, get) => ({
  offsetMs: null,
  nowMs: null,
  serverUtcOffsetMinutes: 0,
  syncWith: (serverTime, receivedAt) => {
    const offsetMs = clockOffsetMs(serverTime, receivedAt);
    set({
      offsetMs,
      nowMs: correctedNow(offsetMs, new Date()).getTime(),
      serverUtcOffsetMinutes: utcOffsetMinutes(serverTime) ?? get().serverUtcOffsetMinutes,
    });
  },
  tick: () => {
    const { offsetMs } = get();
    if (offsetMs !== null) {
      set({ nowMs: correctedNow(offsetMs, new Date()).getTime() });
    }
  },
}));

const TICK_MS = 15_000;

let tickUsers = 0;
let tickTimer: ReturnType<typeof setInterval> | undefined;

function startTicking(): () => void {
  tickUsers += 1;
  if (tickUsers === 1) {
    tickTimer = setInterval(() => useServerClockStore.getState().tick(), TICK_MS);
  }
  return () => {
    tickUsers -= 1;
    if (tickUsers === 0) {
      clearInterval(tickTimer);
      tickTimer = undefined;
    }
  };
}

export function useCorrectedNow(): Date | undefined {
  const nowMs = useServerClockStore((state) => state.nowMs);
  useEffect(startTicking, []);
  return nowMs === null ? undefined : new Date(nowMs);
}
