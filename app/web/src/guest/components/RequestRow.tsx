import { Link } from 'react-router';
import type { GuestTask } from '../api/types';
import type { RequestLink } from '../lib/back-target';
import { stageOf } from '../lib/stage';
import { Icon } from './Icon';
import { RequestIcon } from './RequestIcon';
import { useRequestMeta } from './RequestMeta';
import { StatusTag } from './StatusTag';

/** 清單的一列，整列是進那一筆進度頁的連結。 */
export function RequestRow({ task, link }: { task: GuestTask; link: RequestLink }) {
  const meta = useRequestMeta();
  const ended = stageOf(task).section === 'ended';
  return (
    <li className="[&+li]:shadow-[inset_0_.5px_0_var(--color-guest-separator)]">
      <Link
        to={link.to}
        state={link.state}
        className="flex min-h-16 w-full items-center gap-3 px-4 py-2.5 text-left text-guest-text no-underline active:bg-guest-fill"
      >
        <RequestIcon type={task.type} size="row" tone={ended ? 'neutral' : 'brand'} />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="line-clamp-2 text-guest-headline font-semibold [overflow-wrap:anywhere]">{task.title ?? ''}</span>
          <span className="text-guest-footnote text-guest-text-secondary tabular-nums [overflow-wrap:anywhere]">
            {meta(task)}
          </span>
        </span>
        <StatusTag task={task} />
        <Icon name="chevron_forward" className="text-[16px] text-guest-text-placeholder" />
      </Link>
    </li>
  );
}
