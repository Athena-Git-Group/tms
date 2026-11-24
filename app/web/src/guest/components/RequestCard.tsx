import type { GuestTask } from '../api/types';
import { RequestIcon } from './RequestIcon';
import { useRequestMeta } from './RequestMeta';
import { StatusTag } from './StatusTag';

export function RequestCard({ task }: { task: GuestTask }) {
  const meta = useRequestMeta();
  return (
    <div className="flex items-start gap-3 rounded-guest-lg bg-guest-surface p-4 shadow-guest-card">
      <RequestIcon type={task.type} size="card" />
      <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
        <StatusTag task={task} />
        <h2 className="m-0 text-guest-request-title font-bold [overflow-wrap:anywhere]">{task.title ?? ''}</h2>
        <span className="text-guest-callout text-guest-text-secondary tabular-nums">{meta(task)}</span>
      </div>
    </div>
  );
}
