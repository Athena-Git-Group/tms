import { PointerActivationConstraints } from '@dnd-kit/dom';
import { DragDropProvider, DragOverlay, PointerSensor, useDraggable, useDroppable } from '@dnd-kit/react';
import { useEffect, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { DeskTask } from '../../api/types';
import { useAssignTask } from '../../features/dispatch';
import { isAssignDept, type AssignDept } from '../../lib/dispatch';
import { dropDeptOf } from '../../lib/drag-dispatch';
import { useBoardDragStore } from '../../stores/board-drag';

/**
 * 總覽看板的拖曳派單：把「待派單」的卡片拖到「待認領」欄的「給房務」「給工務」放開，就派給那個部門。
 * 放開之後和按鈕派單走同一支 `useAssignTask`（`via: 'drag'`），浮條、復原、撞單、送出失敗都和按鈕相同。
 *
 * 不用瀏覽器內建的拖放，它在觸控螢幕上不會動；滑鼠與手指走同一套指標事件（拖曳套件選型見 implement-notes 的 T004）。
 * 只掛指標的感應器、不掛鍵盤的：拖曳派單沒有鍵盤版，掛了鍵盤感應器，在卡片上按 Enter 會變成起拖而不是打開詳情。
 */

/**
 * 滑鼠按住移動超過 6px 才起拖，不到就算點一下；手指要按住約 0.25 秒（容許 5px 抖動）才起拖，
 * 短按留給「打開詳情」、直接滑留給「捲動看板」。
 */
const SENSORS = [
  PointerSensor.configure({
    activationConstraints(event) {
      if (event.pointerType === 'touch') {
        return [new PointerActivationConstraints.Delay({ value: 250, tolerance: 5 })];
      }
      return [new PointerActivationConstraints.Distance({ value: 6 })];
    },
  }),
];

/** 正在拖的那張單，給控制列提示、拖放區與「其他格變淡」用；沒有人在拖時是 `null`。 */
export function useDraggingTask(): DeskTask | null {
  return useBoardDragStore((state) => state.dragging);
}

interface CardDragData {
  task: DeskTask;
}

interface ZoneDropData {
  dept: AssignDept;
}

/**
 * 拖曳派單和派單動作的唯一接點，包住整個看板頁。看板頁只管放卡片（`useCardDrag`）與拖放區（`DropZone`），
 * 起拖、放開、取消要不要派、派給誰，都在這裡決定。`renderCard` 畫跟著游標或手指走的那一張卡片；
 * 它只是看起來一樣，不能點、也不給螢幕報讀軟體，免得畫面上同一張單有兩顆按鈕。
 *
 * 拖曳中的那張單放在 `stores/board-drag.ts`。看板在拖曳中離開畫面（換頁、登出）時套件不會通知放開，
 * 所以這裡卸下時自己清掉，下次回到看板才不會停在拖曳中的樣子。
 */
export function BoardDragDispatch({
  children,
  renderCard,
}: {
  children: ReactNode;
  renderCard: (task: DeskTask) => ReactNode;
}) {
  const assignTask = useAssignTask();
  const dragging = useDraggingTask();
  const { startDrag, endDrag } = useBoardDragStore.getState();
  useEffect(() => () => useBoardDragStore.getState().reset(), []);
  return (
    <DragDropProvider
      sensors={SENSORS}
      onDragStart={(event) => {
        const task = (event.operation.source?.data as CardDragData | undefined)?.task;
        if (task) startDrag(task);
      }}
      onDragEnd={(event) => {
        endDrag();
        const task = (event.operation.source?.data as CardDragData | undefined)?.task;
        const release = releasePointOf(event.nativeEvent);
        const dept = dropDeptOf({
          canceled: event.canceled,
          targetDept: (event.operation.target?.data as ZoneDropData | undefined)?.dept,
          targetIsCurrent: release === undefined || samePoint(release, event.operation.position.current),
          releasedOverDept: release && dropZoneAt(release),
        });
        if (task && dept) void assignTask(task, dept, 'drag');
      }}
    >
      {children}
      <DragOverlay dropAnimation={null} className="pointer-events-none">
        {dragging && (
          <div aria-hidden="true" inert className="w-[184px] rotate-2 rounded-card opacity-95 shadow-[0_12px_28px_rgba(0,0,0,0.22)]">
            {renderCard(dragging)}
          </div>
        )}
      </DragOverlay>
    </DragDropProvider>
  );
}

interface Point {
  x: number;
  y: number;
}

/** 放開的那一點（視窗座標）；不是指標事件放開的（例如按 Esc）是 `undefined`。 */
function releasePointOf(event: Event | undefined): Point | undefined {
  return event instanceof PointerEvent ? { x: event.clientX, y: event.clientY } : undefined;
}

/**
 * 套件記的指標位置是不是就是放開那一點。套件把指標移動延到下一個畫面更新才處理，
 * 放開卻馬上處理，所以同一個畫面內「移動後放開」時，套件的位置與落點都還停在上一次移動。
 */
function samePoint(release: Point, position: Point): boolean {
  return Math.abs(release.x - position.x) < 1 && Math.abs(release.y - position.y) < 1;
}

/** 這一點底下的拖放區是哪個部門；底下不是拖放區時是 `undefined`。 */
function dropZoneAt({ x, y }: Point): AssignDept | undefined {
  const zone = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-drop-dept]');
  const dept = zone?.dataset.dropDept;
  return isAssignDept(dept) ? dept : undefined;
}

/**
 * 讓一張「待派單」的卡片拖得動。回傳的 `ref` 掛在卡片上；`source` 表示這張正在被拖，原位的卡片變半透明留著當佔位。
 * 只給拖得動的卡片用：其他狀態的卡片不要註冊，註冊成停用的話套件會把它標成停用，卡片就點不開了。
 */
export function useCardDrag(task: DeskTask) {
  const { ref, isDragSource } = useDraggable<CardDragData>({ id: task.id, data: { task } });
  return { ref, source: isDragSource };
}

/** 控制列右側的提示：平時說明哪些卡片拖得動，拖曳中換成品牌色粗體的那一句。 */
export function DragHint() {
  const { t } = useTranslation();
  const dragging = useDraggingTask() !== null;
  return (
    <span
      role="status"
      className={`ml-auto text-right text-[13px] ${dragging ? 'font-bold text-brand' : 'text-text-2'}`}
    >
      {dragging ? t('board.dragHint.dragging') : t('board.dragHint.idle')}
    </span>
  );
}

/**
 * 「待認領」欄最上面的一個拖放區：虛線框、品牌淺底，第一行「給房務」或「給工務」，第二行說明。
 * 卡片滑到上面時框線變實、底色變深、略放大，說明換成「放開就完成派單」。
 *
 * @param id 整個看板唯一的編號；分泳道時同一個部門的拖放區只會出現在一列，但仍以泳道區分
 */
export function DropZone({ id, dept }: { id: string; dept: AssignDept }) {
  const { t } = useTranslation();
  const { ref, isDropTarget } = useDroppable<ZoneDropData>({ id, data: { dept } });
  return (
    <div
      ref={ref}
      data-drop-dept={dept}
      data-over={isDropTarget || undefined}
      className="flex min-h-[72px] flex-col items-center justify-center gap-0.5 rounded-xl border-2 border-dashed border-[#7FD4DE] bg-[rgba(200,232,237,0.45)] p-1.5 text-center text-brand transition-[transform,background-color] duration-[120ms] ease-out data-over:scale-[1.03] data-over:border-solid data-over:border-brand data-over:bg-brand-container"
    >
      <b className="text-[15px] font-extrabold">{t(`board.dropZone.name.${dept}`)}</b>
      <span className="text-xs font-semibold">
        {isDropTarget ? t('board.dropZone.release') : t(`board.dropZone.into.${dept}`)}
      </span>
    </div>
  );
}
