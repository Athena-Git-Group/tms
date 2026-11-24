import { PointerActivationConstraints } from '@dnd-kit/dom';
import { DragDropProvider, DragOverlay, PointerSensor, useDraggable, useDroppable } from '@dnd-kit/react';
import { useEffect, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { DeskTask } from '../../api/types';
import { useAssignTask } from '../../features/dispatch';
import { isAssignDept, type AssignDept } from '../../lib/dispatch';
import { dropDeptOf } from '../../lib/drag-dispatch';
import { useBoardDragStore } from '../../stores/board-drag';

const TOUCH_HOLD_TO_DRAG_MS = 250;
const TOUCH_MOVE_DURING_HOLD_CANCELS_DRAG_PX = 5;
const POINTER_MOVE_TO_START_DRAG_PX = 6;

/**
 * 拖曳派單用拖放套件的指標感應器，不用瀏覽器內建的拖放，因為內建拖放在觸控螢幕上不會觸發。
 * 只掛指標感應器，不掛鍵盤感應器。拖曳派單沒有鍵盤操作版本，掛了鍵盤感應器的話，
 * 在卡片上按 Enter 會開始拖曳，而不是打開詳情。
 */
const SENSORS = [
  PointerSensor.configure({
    activationConstraints(event) {
      if (event.pointerType === 'touch') {
        return [
          new PointerActivationConstraints.Delay({
            value: TOUCH_HOLD_TO_DRAG_MS,
            tolerance: TOUCH_MOVE_DURING_HOLD_CANCELS_DRAG_PX,
          }),
        ];
      }
      return [new PointerActivationConstraints.Distance({ value: POINTER_MOVE_TO_START_DRAG_PX })];
    },
  }),
];

export function useDraggingTask(): DeskTask | null {
  return useBoardDragStore((state) => state.dragging);
}

interface CardDragData {
  task: DeskTask;
}

interface ZoneDropData {
  dept: AssignDept;
}

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
  // 看板在拖曳中離開畫面（換頁、登出）時套件不會通知放開，所以這裡卸下時自己清掉。
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

function releasePointOf(event: Event | undefined): Point | undefined {
  return event instanceof PointerEvent ? { x: event.clientX, y: event.clientY } : undefined;
}

function samePoint(release: Point, position: Point): boolean {
  return Math.abs(release.x - position.x) < 1 && Math.abs(release.y - position.y) < 1;
}

function dropZoneAt({ x, y }: Point): AssignDept | undefined {
  const zone = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-drop-dept]');
  const dept = zone?.dataset.dropDept;
  return isAssignDept(dept) ? dept : undefined;
}

/**
 * 只給拖得動的卡片用：其他狀態的卡片不要註冊，註冊成停用的話套件會把它標成停用，卡片就點不開了。
 */
export function useCardDrag(task: DeskTask) {
  const { ref, isDragSource } = useDraggable<CardDragData>({ id: task.id, data: { task } });
  return { ref, source: isDragSource };
}

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
