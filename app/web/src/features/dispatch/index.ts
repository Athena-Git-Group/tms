/**
 * 派單動作的落點：按鈕派單、拖曳派單、詳情面板的派單區三個入口共用的同一套
 * 送出、派單浮條、復原、撞單與送出失敗的處理。撞單對話框與撞單時的看板處理也給當場回答用。
 */
export { AssignToastBar, DispatchNoticeDialog } from './DispatchOverlays';
export { ConflictDialog } from './ConflictDialog';
export { settleConflictOnBoard } from './settle-conflict';
export { useAssignTask, useUndoAssign } from './use-dispatch';
