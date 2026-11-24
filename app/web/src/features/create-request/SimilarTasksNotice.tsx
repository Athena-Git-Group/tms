import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { deskBoardQuery } from '../../api/desk';
import { useCorrectedNow } from '../../stores/server-clock';
import { similarTaskLines, type CreateRequestDraft } from './create-request-logic';

export function SimilarTasksNotice({ draft }: { draft: CreateRequestDraft }) {
  const { t } = useTranslation();
  const now = useCorrectedNow();
  const { data: boardTasks } = useQuery({
    ...deskBoardQuery,
    refetchOnMount: false,
    select: (board) => board.tasks,
  });
  const lines = similarTaskLines(draft, boardTasks ?? [], now ?? new Date());
  if (lines.length === 0) return null;

  return (
    <div role="status" className="flex items-start gap-3 rounded-card bg-warm-bg px-4 py-3 motion-safe:animate-drop-in">
      <svg viewBox="0 0 24 24" aria-hidden="true" className="mt-px size-[22px] shrink-0 fill-[#FF9500]">
        <path d="M12 3 2 21h20zm-1 6h2v6h-2zm0 8h2v2h-2z" />
      </svg>
      <div className="flex min-w-0 flex-auto flex-col gap-1">
        <b className="text-[15px] font-bold text-[#3A1C00]">{t('createRequest.similar.title')}</b>
        {lines.map((line) => (
          <span key={line.taskId} className="text-[13px] leading-[1.45] text-warm-fg [overflow-wrap:anywhere]">
            {line.assigneeName
              ? t('createRequest.similar.rowWithAssignee', {
                  title: line.title,
                  status: t(`detail.tags.${line.state}`),
                  name: line.assigneeName,
                  ago: line.minutes < 1 ? t('answer.justNow') : t('answer.minutesAgo', { minutes: line.minutes }),
                })
              : t('createRequest.similar.row', {
                  title: line.title,
                  status: t(`detail.tags.${line.state}`),
                  ago: line.minutes < 1 ? t('answer.justNow') : t('answer.minutesAgo', { minutes: line.minutes }),
                })}
          </span>
        ))}
        <span className="text-[13px] leading-[1.45] text-warm-fg">{t('createRequest.similar.tail')}</span>
      </div>
    </div>
  );
}
