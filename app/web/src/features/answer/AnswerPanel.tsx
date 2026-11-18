import { useQuery } from '@tanstack/react-query';
import { useEffect, useId, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { listDeskQuickReplies, readDeskTask } from '../../api/desk';
import { AlertDialog } from '../../components/AlertDialog';
import { SkeletonBlock } from '../../components/SkeletonBlock';
import { ANSWER_MAX_LENGTH, answerQuoteHead } from '../../lib/answer-text';
import { cardTitle } from '../../lib/card-text';
import { originalLineText } from '../../lib/triage-text';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import { useCorrectedNow } from '../../stores/server-clock';
import { ConflictDialog } from '../dispatch';
import { useAnswerReply } from './use-answer-reply';

/** 常用回覆的查詢設定：飯店設定，全館共用一份；開面板時讀一次，不需要即時同步。 */
const quickRepliesQuery = {
  queryKey: ['desk-quick-replies'],
  queryFn: listDeskQuickReplies,
  retry: false,
  staleTime: Infinity,
} as const;

/**
 * 當場回答面板：置中、後面是半透明遮罩，標題是「當場回答 · {房號}」。按「取消」或點遮罩關閉，打到一半的字不保留。
 *
 * 由上到下：住客原話白卡、常用回覆（點了把全文帶進回覆框）、回覆框與字數、說明、底部的「回覆並結案」。
 * 帶入、字數、能不能送與送出後的處理都在 `useAnswerReply`，這裡只管畫。
 * 撞單與沒送出時在面板上面跳只有「好」的對話框，按「好」之後面板關不關由 `useAnswerReply` 決定。
 */
export function AnswerPanel({ taskId }: { taskId: string }) {
  const { t } = useTranslation();
  const titleId = useId();
  const replyId = useId();
  const replyBox = useRef<HTMLTextAreaElement>(null);
  const now = useCorrectedNow();
  const closeAnswer = useDeskPanelsStore((state) => state.closeAnswer);
  const { data: task } = useQuery({ queryKey: ['desk-task', taskId], queryFn: () => readDeskTask(taskId) });
  const quickReplies = useQuery(quickRepliesQuery);
  const reply = useAnswerReply(taskId, task?.roomNo ?? '');

  // 打開就把焦點放進回覆框，櫃檯可以直接打字，按 Esc 也關得掉。
  useEffect(() => {
    replyBox.current?.focus();
  }, []);

  const originalLine = task ? originalLineText(task, t) : null;
  const { notice } = reply;
  const closeNotice = () => {
    reply.closeNotice();
    // 沒送出時面板還在，焦點回到回覆框，可以直接再送；撞單時面板接著就關掉。
    replyBox.current?.focus();
  };

  return (
    <>
      <div
        className="fixed inset-0 z-30 flex items-center justify-center bg-black/[.32]"
        onClick={(event) => {
          if (event.target === event.currentTarget) closeAnswer();
        }}
        onKeyDown={(event) => {
          if (event.key === 'Escape') closeAnswer();
        }}
      >
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          className="flex max-h-answer-panel w-answer-panel flex-col overflow-hidden rounded-card bg-bg shadow-[0_20px_60px_rgba(0,0,0,.25)]"
        >
          <div className="grid min-h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center px-2 pt-1.5">
            <span>
              <button
                type="button"
                onClick={closeAnswer}
                className="inline-flex min-h-touch items-center border-none bg-transparent px-2 text-[17px] text-brand"
              >
                {t('answer.cancel')}
              </button>
            </span>
            <h2 id={titleId} className="m-0 text-center text-[17px] font-semibold whitespace-nowrap">
              {t('answer.title', { roomNo: task?.roomNo ?? '' })}
            </h2>
            <span />
          </div>
          <div className="flex min-h-0 flex-auto flex-col gap-3.5 overflow-y-auto px-5 pt-1 pb-4 [&>*]:shrink-0">
            <div aria-busy={!task} className="flex flex-col gap-1 rounded-card bg-surface px-4 py-3.5">
              {task ? (
                <>
                  <small className="text-[13px] text-text-2">{answerQuoteHead(task, now, t)}</small>
                  <b className="text-[17px] leading-[1.4] font-semibold [overflow-wrap:anywhere]">{cardTitle(task, t)}</b>
                  {originalLine && <small className="text-[13px] text-text-2">{originalLine}</small>}
                </>
              ) : (
                <SkeletonBlock className="h-12 rounded-lg" />
              )}
            </div>
            {quickReplies.isPending ? (
              <div aria-busy="true" className="flex flex-wrap gap-2">
                {[0, 1, 2].map((index) => (
                  <SkeletonBlock key={index} className="h-11 w-24 rounded-full" />
                ))}
              </div>
            ) : (
              quickReplies.data &&
              quickReplies.data.length > 0 && (
                <div className="flex flex-col gap-2">
                  <span className="px-1 text-[13px] leading-[1.45] text-text-2">{t('answer.quickReplies')}</span>
                  <div className="flex flex-wrap gap-2">
                    {quickReplies.data.map((quickReply) => {
                      const picked = reply.pickedKey === quickReply.key;
                      return (
                        <button
                          key={quickReply.key}
                          type="button"
                          aria-pressed={picked}
                          disabled={reply.submitting}
                          onClick={() => reply.pick(quickReply)}
                          className={`min-h-touch rounded-full border border-solid px-3.5 text-sm font-semibold ${
                            picked ? 'border-brand bg-brand text-white' : 'border-line bg-surface text-text'
                          }`}
                        >
                          {quickReply.title}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )
            )}
            <div className="flex flex-col gap-1.5 rounded-card bg-surface px-4 py-3">
              <label htmlFor={replyId} className="text-[13px] font-semibold text-text-2">
                {t('answer.replyLabel')}
              </label>
              <textarea
                id={replyId}
                ref={replyBox}
                rows={3}
                readOnly={reply.submitting}
                placeholder={t('answer.replyPlaceholder')}
                value={reply.text}
                onChange={(event) => reply.edit(event.target.value)}
                className="w-full resize-none border-none bg-transparent text-base leading-[1.45] text-text outline-none"
              />
              <span className="self-end text-xs text-text-3">
                {t('answer.counter', { count: reply.length, max: ANSWER_MAX_LENGTH })}
              </span>
            </div>
            <span className="px-1 text-[13px] leading-[1.45] text-text-2">{t('answer.note')}</span>
          </div>
          <div className="shrink-0 px-5 pt-2.5 pb-4">
            <button
              type="button"
              disabled={!reply.canSubmit}
              onClick={reply.submit}
              className="min-h-[54px] w-full rounded-full border-none bg-brand text-[17px] font-semibold text-white disabled:bg-line"
            >
              {t('answer.submit')}
            </button>
          </div>
        </div>
      </div>
      {/* 對話框放在面板外面、排在它後面：蓋在面板上，按 Esc 只關對話框，不會連面板一起關。 */}
      {/* 撞單用和派單共用的 `ConflictDialog`，最後一句換成「不需要再回覆。」。 */}
      {notice?.kind === 'answerConflict' && (
        <ConflictDialog notice={notice} action="answer" onClose={closeNotice} />
      )}
      {notice?.kind === 'answerNotSent' && (
        <AlertDialog
          title={t('answer.notSent.title')}
          body={t('answer.notSent.body', { roomNo: notice.roomNo })}
          onClose={closeNotice}
        />
      )}
    </>
  );
}
