import type { QueryClient } from '@tanstack/react-query';
import { deskBoardQuery } from '../../api/desk';
import type { DeskTask } from '../../api/types';
import type { ConflictContext } from '../../lib/conflict-text';
import { withConflictSettled, withTaskReplaced } from '../../lib/dispatch';

/**
 * 撞單時看板快取的處理，派單與當場回答共用：照後端說的「這張單怎麼離開待派單」馬上把它移出待派單。
 * 對話框說「看板已更新成最新狀態」，畫面不能還留著這張單、或停在先畫的「已派出」等重讀；
 * 看板讀取被暫停時也一樣要先變。之後的整份重讀由呼叫端自己發。
 *
 * 派單按下去時已經先畫成「進了待認領」，所以要帶 `before`（按下前的那一張），
 * 先換回按下前的樣子再移，否則被當場回答或住客收回的單會帶著櫃檯剛選的部門。
 * 後端沒說是哪一種時，這張單就停在按下前的樣子等重讀。
 */
export function settleConflictOnBoard(
  queryClient: QueryClient,
  taskId: string,
  context: ConflictContext,
  before?: DeskTask,
) {
  queryClient.setQueryData(deskBoardQuery.queryKey, (board) =>
    board ? withConflictSettled(before ? withTaskReplaced(board, before) : board, taskId, context) : board,
  );
}
