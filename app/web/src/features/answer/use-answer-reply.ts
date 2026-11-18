import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { answerDeskTask, deskBoardQuery } from '../../api/desk';
import type { DeskQuickReply, DeskTask } from '../../api/types';
import { ANSWER_MAX_LENGTH, answerLength, clampAnswer, isSendableAnswer, stripAnswer } from '../../lib/answer-text';
import { answerFailureNotice, withTaskReplaced, type AnswerNotice } from '../../lib/dispatch';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import { settleConflictOnBoard } from '../dispatch';

export function useAnswerReply(taskId: string, roomNo: string) {
  const queryClient = useQueryClient();
  const closeAnswer = useDeskPanelsStore((state) => state.closeAnswer);
  const [reply, setReply] = useState('');
  const [pickedKey, setPickedKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<AnswerNotice | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: deskBoardQuery.queryKey });
    void queryClient.invalidateQueries({ queryKey: ['desk-task', taskId] });
  };

  const answer = useMutation({
    mutationFn: (text: string) => answerDeskTask(taskId, { answer: text }),
    onSuccess: (answered: DeskTask) => {
      queryClient.setQueryData(deskBoardQuery.queryKey, (board) => (board ? withTaskReplaced(board, answered) : board));
      refresh();
      closeAnswer();
    },
    onError: (error: unknown) => {
      const failure = answerFailureNotice(error, roomNo);
      if (failure?.kind === 'answerConflict') {
        settleConflictOnBoard(queryClient, taskId, failure.context);
        refresh();
      }
      setNotice(failure);
    },
  });

  const submitting = answer.isPending;

  return {
    notice,
    closeNotice() {
      if (notice?.kind === 'answerConflict') closeAnswer();
      setNotice(null);
    },
    text: reply,
    length: answerLength(reply),
    pickedKey,
    submitting,
    canSubmit: isSendableAnswer(reply) && !submitting,
    pick(quickReply: DeskQuickReply) {
      if (submitting) return;
      setReply(clampAnswer(quickReply.body));
      setPickedKey(quickReply.key);
    },
    edit(text: string) {
      if (submitting) return;
      if (answerLength(text) > ANSWER_MAX_LENGTH && answerLength(reply) >= ANSWER_MAX_LENGTH) return;
      setReply(clampAnswer(text));
      setPickedKey(null);
    },
    submit() {
      if (!isSendableAnswer(reply) || submitting) return;
      answer.mutate(stripAnswer(reply));
    },
  };
}
