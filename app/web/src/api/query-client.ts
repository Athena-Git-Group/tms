import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient();

export const REREAD_INTERVAL_MS = 4_000;

export function retryAfterFailure(query: { state: { status: string } }): number | false {
  return query.state.status === 'error' ? REREAD_INTERVAL_MS : false;
}
