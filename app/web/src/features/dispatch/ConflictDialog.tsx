import { useTranslation } from 'react-i18next';
import { AlertDialog } from '../../components/AlertDialog';
import { conflictText, type ConflictAction } from '../../lib/conflict-text';
import type { ConflictNotice } from '../../lib/dispatch';

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
