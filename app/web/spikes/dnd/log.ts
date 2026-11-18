/** 記下瀏覽器實際送到頁面的指標事件與套件回報的拖曳事件，給 spike 的觀察與自動化斷言用。 */
export interface SpikeLog {
  pointer: { type: string; pointerType: string; isTrusted: boolean }[];
  other: string[];
  lib: string[];
}

declare global {
  interface Window {
    __spike: SpikeLog;
  }
}

export function installLog(): void {
  window.__spike = { pointer: [], other: [], lib: [] };
  for (const type of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) {
    document.addEventListener(
      type,
      (event) => {
        const e = event as PointerEvent;
        window.__spike.pointer.push({ type, pointerType: e.pointerType, isTrusted: e.isTrusted });
      },
      true,
    );
  }
  for (const type of ['mousedown', 'mouseup', 'touchstart', 'touchend', 'touchcancel', 'click', 'contextmenu']) {
    document.addEventListener(type, () => window.__spike.other.push(type), true);
  }
}

export function logLib(message: string): void {
  window.__spike.lib.push(message);
}
