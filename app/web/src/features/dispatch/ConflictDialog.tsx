import { useTranslation } from 'react-i18next';
import { AlertDialog } from '../../components/AlertDialog';
import { conflictText, type ConflictAction } from '../../lib/conflict-text';
import type { ConflictNotice } from '../../lib/dispatch';

/**
 * 撞單對話框，派單與當場回答共用：只有一顆「好」，標題與內文照後端回的 `context` 組
 * （`action` 決定最後一句是「不需要再派單」還是「不需要再回覆」）。
 * 按「好」之後要做什麼由呼叫端的 `onClose` 決定：派單只關對話框，當場回答連面板一起關。
 * 看板快取在撞單當下就由 `settleConflictOnBoard` 處理好了，這裡不碰。
 */
export function ConflictDialog({
  notice,
  action,
  onClose,
}: {
  notice: ConflictNotice;
  action: ConflictAction;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const { title, body } = conflictText(notice.context, notice.roomNo, action, t);
  return <AlertDialog title={title} body={body} onClose={onClose} />;
}
