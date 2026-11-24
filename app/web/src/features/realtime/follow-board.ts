import type { QueryClient } from '@tanstack/react-query';
import { deskBoardQuery, receiveCardPush } from '../../api/desk';
import { businessDayRereadDelayMs } from '../../lib/board-sync';
import { correctedNow } from '../../lib/server-clock';
import { useServerClockStore } from '../../stores/server-clock';
import { createBoardRereader } from './refetch-on-signal';
import { parseStreamTask } from './stream-task';
import type { StreamEvent } from './stream-client';

export function followBoard(queryClient: QueryClient): { onStreamEvent: (event: StreamEvent) => void; stop: () => void } {
  const reread = createBoardRereader(queryClient);
  let businessDayTimer: ReturnType<typeof setTimeout> | undefined;

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
