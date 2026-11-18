import { useEffect } from 'react';
import { create } from 'zustand';
import { clockOffsetMs, correctedNow, utcOffsetMinutes } from '../lib/server-clock';

/**
 * 櫃檯畫面共用的「現在」：用後端回應的 `serverTime` 校正過的時間，只有一份。
 * 等待膠囊、數量卡、頁副標與詳情面板都讀這裡，同一張單在各處算出的分鐘數才會一樣。
 * 只放在記憶體、不寫進任何儲存空間，各種狀態的去留集中列在 `lib/persistence.ts`。
 */
interface ServerClockState {
  /** 後端時間減掉這台裝置時間的差（毫秒）；還沒收到過後端時間時是 `null`。 */
  offsetMs: number | null;
  /** 校正後的現在（毫秒）；還沒收到過後端時間時是 `null`。所有畫面讀同一個值，不各自取裝置時間。 */
  nowMs: number | null;
  /**
   * 後端伺服器的時區偏移（分鐘，`+08:00` 是 480），從 `serverTime` 尾端讀出；飯店的時區就是伺服器的時區。
   * 畫面上的「時:分」照這個偏移顯示。還沒收到過後端時間時是 0；收到的字串讀不出偏移時沿用上一次的值。
   */
  serverUtcOffsetMinutes: number;
  /** 收到帶 `serverTime` 的回應時呼叫，`receivedAt` 是收到回應當下這台裝置的時間。 */
  syncWith: (serverTime: string, receivedAt: Date) => void;
  /** 用記下的差重算一次現在。 */
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

/** 校正後的現在多久重算一次。畫面上的分鐘數以整分為單位，15 秒內一定跟得上。 */
const TICK_MS = 15_000;

/** 整個頁面只開一個計時器，用到的畫面有幾個就記幾個，全部離開畫面才停掉。 */
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

/**
 * 校正後的現在，每 15 秒更新一次；還沒收到過後端時間時是 `undefined`。
 * 要算「等 N 分」「接 N 分」這類分鐘數的畫面都用這個，不要自己 `new Date()`。
 */
export function useCorrectedNow(): Date | undefined {
  const nowMs = useServerClockStore((state) => state.nowMs);
  useEffect(startTicking, []);
  return nowMs === null ? undefined : new Date(nowMs);
}
