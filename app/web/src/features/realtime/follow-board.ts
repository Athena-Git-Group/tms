import type { QueryClient } from '@tanstack/react-query';
import { deskBoardQuery, receiveCardPush } from '../../api/desk';
import { businessDayRereadDelayMs } from '../../lib/board-sync';
import { correctedNow } from '../../lib/server-clock';
import { useServerClockStore } from '../../stores/server-clock';
import { createBoardRereader } from './refetch-on-signal';
import { parseStreamTask } from './stream-task';
import type { StreamEvent } from './stream-client';

/**
 * 讓看板快取跟上現場，每條連線建一個。
 *
 * - `task` 帶著卡片時直接換那一張（規則見 `lib/board-sync.ts`）；沒帶、看不懂、步數跳號或營業日不同時整份重讀。
 *   開著的詳情面板或當場回答面板是那張單時，也重讀那張單的詳情，因為卡片不帶歷程與詳情才有的欄位。
 * - `resync`、`recovered`：整份重讀，補上連線以外或降級期間的變化。
 * - 到了換日時刻就算沒有任何訊號也整份重讀一次，昨天結束的卡片才會從看板上拿掉；
 *   後端只在單有變化時送訊號，深夜整館沒有變化時，畫面不會從訊號知道營業日換了。
 *
 * 回傳的 `stop` 停掉換日的計時與對快取的監看，連線關掉時呼叫。
 */
export function followBoard(queryClient: QueryClient): { onStreamEvent: (event: StreamEvent) => void; stop: () => void } {
  const reread = createBoardRereader(queryClient);
  let businessDayTimer: ReturnType<typeof setTimeout> | undefined;

  // 每讀到一份新的看板就重新算一次，讀回來還是舊營業日時自然排成一分鐘後再讀。
  function scheduleBusinessDayReread() {
    clearTimeout(businessDayTimer);
    const board = queryClient.getQueryData(deskBoardQuery.queryKey);
    if (!board) return;
    const nowMs = correctedNow(useServerClockStore.getState().offsetMs ?? 0, new Date()).getTime();
    const delay = businessDayRereadDelayMs(board, nowMs);
    if (delay !== null) businessDayTimer = setTimeout(() => reread('stale'), delay);
  }

  const unsubscribe = queryClient.getQueryCache().subscribe((event) => {
    if (
      event.type === 'updated' &&
      event.action.type === 'success' &&
      !event.action.manual &&
      event.query.queryKey[0] === deskBoardQuery.queryKey[0]
    ) {
      scheduleBusinessDayReread();
    }
  });
  scheduleBusinessDayReread();

  function onStreamEvent(event: StreamEvent) {
    if (event.event === 'resync' || event.event === 'recovered') {
      reread('reconnected');
      return;
    }
    if (event.event !== 'task') return;
    const task = parseStreamTask(event.data);
    const taskId = task.kind === 'card' ? task.push.taskId : task.taskId;
    if (taskId !== null) void queryClient.invalidateQueries({ queryKey: ['desk-task', taskId] });
    if (task.kind === 'noCard' || receiveCardPush(queryClient, task.push).needsFullRead) reread('stale');
  }

  return {
    onStreamEvent,
    stop() {
      clearTimeout(businessDayTimer);
      unsubscribe();
    },
  };
}
