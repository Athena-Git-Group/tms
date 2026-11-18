import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { answerDeskTask, deskBoardQuery } from '../../api/desk';
import type { DeskQuickReply, DeskTask } from '../../api/types';
import { ANSWER_MAX_LENGTH, answerLength, clampAnswer, isSendableAnswer, stripAnswer } from '../../lib/answer-text';
import { answerFailureNotice, withTaskReplaced, type AnswerNotice } from '../../lib/dispatch';
import { useDeskPanelsStore } from '../../stores/desk-panels';
import { settleConflictOnBoard } from '../dispatch';

/**
 * 當場回答面板的回覆框規則，面板只管畫。字數一律以 code point 計，和後端 `DeskAnswerText` 同一把尺。
 *
 * - 點常用回覆把全文帶進回覆框，超過 120 字只帶前 120 字，這一則變成選中的樣子。
 * - 自己打字或改字就沒有任何一則是選中的；打滿 120 字之後多打的字不收。
 * - 去掉頭尾空白後是空的不能送；送出的是去掉頭尾空白後的字。
 * - 送出中回覆框與常用回覆都不能改，避免畫面上的字和送出去的不一樣。
 * - 送出成功用回傳的單換掉看板上那一張，再整份重讀，然後關面板；當場回答沒有復原，所以不出浮條。
 * - 沒送成時跳只有「好」的對話框（`notice`）：
 *   撞單（別人先派走、先回答了，或住客剛收回）時這張單馬上移出待派單並重讀看板，按「好」關掉對話框與面板；
 *   其他失敗（例如斷線）時單不變，按「好」只關對話框，回覆框的字與選中的常用回覆都留著，可以直接再送一次。
 */
export function useAnswerReply(taskId: string, roomNo: string) {
  const queryClient = useQueryClient();
  const closeAnswer = useDeskPanelsStore((state) => state.closeAnswer);
  const [reply, setReply] = useState('');
  /** 帶入後還沒改過字的那一則常用回覆的鍵；改了字就沒有任何一則是選中的。 */
  const [pickedKey, setPickedKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<AnswerNotice | null>(null);

  const refresh = () => {
    // 頁首的今日張數與詳情面板也跟著變了，整份重讀。
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
        // 撞單時這張單已經不在待派單：和派單撞單一樣先把它移出待派單，再整份重讀。
        settleConflictOnBoard(queryClient, taskId, failure.context);
        refresh();
      }
      setNotice(failure);
    },
  });

  const submitting = answer.isPending;

  return {
    /** 沒送成時要跳的對話框；沒有是 `null`。 */
    notice,
    /** 對話框上的「好」：撞單時連面板一起關，沒送出時回到面板。 */
    closeNotice() {
      if (notice?.kind === 'answerConflict') closeAnswer();
      setNotice(null);
    },
    /** 回覆框裡的字，還沒去掉頭尾空白。 */
    text: reply,
    /** 回覆框目前有幾個字，畫在「N / 120」。 */
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
      // 框子滿了還在中間插字時若改成截尾，櫃檯打一個字就會默默少掉最後一個字，所以整筆不收。
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
