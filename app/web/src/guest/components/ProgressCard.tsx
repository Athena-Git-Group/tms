import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { GuestTask } from '../api/types';
import type { RequestLink } from '../lib/back-target';
import { homeCardLine, stageOf, STEP_TITLES } from '../lib/stage';
import { hhmm } from '../lib/time';
import { Icon } from './Icon';
import { RequestIcon } from './RequestIcon';

export function ProgressCard({ task, link }: { task: GuestTask; link: RequestLink }) {
  const { t } = useTranslation();
  const current = stageOf(task).step ?? 0;
  const line = homeCardLine(task);
  return (
    <Link
      to={link.to}
      state={link.state}
      className="flex flex-col gap-3 rounded-guest-lg bg-guest-surface p-4 text-guest-text no-underline shadow-guest-card active:scale-[.98]"
    >
      <span className="flex items-start gap-3">
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="text-guest-footnote text-guest-text-secondary">{t('guest.myRequests.eyebrow')}</span>
          <span className="line-clamp-2 text-guest-card-title font-bold tracking-[.2px] [overflow-wrap:anywhere]">
            {task.title ?? ''}
          </span>
        </span>
        <RequestIcon type={task.type} size="card" />
      </span>
      <span className="flex flex-col gap-1.5">
        <span aria-hidden="true" className="grid grid-cols-4 gap-1">
          {STEP_TITLES.map((key, index) => (
            <span
              key={key}
              className={`h-1 rounded-guest-pill ${index <= current ? 'bg-guest-brand-primary' : 'bg-guest-fill'}`}
            />
          ))}
        </span>
        <ol className="m-0 grid list-none grid-cols-4 gap-1 p-0">
          {STEP_TITLES.map((key, index) => (
            <li
              key={key}
              aria-current={index === current ? 'step' : undefined}
              className={`truncate text-guest-caption ${index <= current ? 'font-semibold text-guest-text' : 'text-guest-text-secondary'}`}
            >
              {t(key)}
            </li>
          ))}
        </ol>
      </span>
      <span className="-mx-4 -mb-4 flex items-center gap-2 border-t-[.5px] border-guest-separator px-4 py-3 text-[15px] leading-5">
        <Icon name="clock" className="text-[20px] text-guest-brand-primary" />
        <span className="min-w-0 flex-1 tabular-nums">
          {line ? t(line.key, { dept: t(line.deptKey), time: hhmm(line.createdAt) }) : null}
        </span>
        <b className="font-semibold text-guest-brand-primary">{t('guest.myRequests.view')}</b>
        <Icon name="chevron_forward" className="text-[16px] text-guest-text-placeholder" />
      </span>
    </Link>
  );
}
