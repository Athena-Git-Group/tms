import type { QueryClient } from '@tanstack/react-query';
import { boardReadCancelCount, deskBoardQuery } from '../../api/desk';

type BoardReadState = 'idle' | 'reading' | 'reading-and-dirty';

export type BoardRereadReason = 'stale' | 'reconnected';

export function createBoardRereader(queryClient: QueryClient): (reason: BoardRereadReason) => void {
  let state: BoardReadState = 'idle';

  function read(next: Exclude<BoardReadState, 'idle'>): void {
    state = next;
    const cancelsBefore = boardReadCancelCount(queryClient);
    void queryClient
      .invalidateQueries({ queryKey: deskBoardQuery.queryKey }, { cancelRefetch: false })
      .then(() => {
        const cancelledByDispatch = boardReadCancelCount(queryClient) !== cancelsBefore;
        if (state === 'reading-and-dirty' && !cancelledByDispatch) read('reading');
        else state = 'idle';
      });
  }

  return (reason) => {
    const board = queryClient.getQueryState(deskBoardQuery.queryKey);
    if (reason === 'stale') {
      if (state !== 'idle') state = 'reading-and-dirty';
      else read(board === undefined || board.fetchStatus === 'idle' ? 'reading' : 'reading-and-dirty');
      return;
    }
    if (state === 'idle' && board?.status === 'success') read('reading');
  };
}
