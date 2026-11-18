import type { QueryClient } from '@tanstack/react-query';
import { deskBoardQuery } from '../../api/desk';

export function refetchForSignal(signal: string, queryClient: QueryClient): void {
  if (signal === 'task') {
    void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
    return;
  }
  if ((signal === 'resync' || signal === 'recovered') &&queryClient.getQueryState(deskBoardQuery.queryKey)?.status === 'success') {
    void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey }, { cancelRefetch: false });
  }
}
