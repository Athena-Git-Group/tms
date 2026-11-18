import type { QueryClient } from '@tanstack/react-query';
import { deskBoardQuery } from '../../api/desk';
import type { DeskTask } from '../../api/types';
import type { ConflictContext } from '../../lib/conflict-text';
import { withConflictSettled, withTaskReplaced } from '../../lib/dispatch';

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
