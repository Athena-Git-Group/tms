import type { DeskTask } from '../api/types';
import type { BoardColumn } from './board-columns';
import { columnOf } from './board-columns';
import type { LaneKind } from './board-view-model';
import type { AssignDept } from './dispatch';

const ZONE_ORDER: readonly AssignDept[] = ['housekeeping', 'engineering'];

export function canDragToDispatch(task: DeskTask): boolean {
  return columnOf(task) === 'triage';
}

export function dropZoneDepts(column: BoardColumn, lane: LaneKind | null, dragging: DeskTask): AssignDept[] {
  if (column !== 'pool') return [];
  if (lane === null) return [...ZONE_ORDER];
  if (lane.kind === 'dept') return ZONE_ORDER.filter((dept) => dept === lane.dept);
  return lane.floor === dragging.floor ? [...ZONE_ORDER] : [];
}

export interface CellWhileDragging {
  zones: AssignDept[];
  faded: boolean;
}

export function cellWhileDragging(
  column: BoardColumn,
  lane: LaneKind | null,
  cellTasks: readonly DeskTask[],
  dragging: DeskTask | null,
): CellWhileDragging {
  if (dragging === null) return { zones: [], faded: false };
  const zones = dropZoneDepts(column, lane, dragging);
  const faded = zones.length === 0 && !cellTasks.some((task) => task.id === dragging.id);
  return { zones, faded };
}

export interface DragRelease {
  canceled: boolean;
  targetDept: AssignDept | undefined;
  targetIsCurrent: boolean;
  releasedOverDept: AssignDept | undefined;
}

/**
 * 取消要最先看：按 Esc 時拖放套件 dnd-kit 可能還記著最後滑過的拖放區，晚一步看就會把取消當成派單。
 *
 * 再看放開那一點底下的拖放區。套件把指標移動延到下一個畫面更新才處理，放開卻馬上處理，
 * 所以在同一個畫面內移動後放開，套件的落點還是上一次移動的結果：可能是空的（手指移到拖放區馬上放開），
 * 也可能是途中滑過的另一個拖放區（拖向「給房務」途中掠過「給工務」）。放開那一點才是使用者要的。
 *
 * 放開那一點底下沒有拖放區時，才採用套件的落點，且只在它是用放開那一點算的時候：
 * 卡片大半蓋在拖放區上、指標稍微在外面時，拖放區已經顯示「放開就完成派單」，放開就要派；
 * 套件落點是舊位置算的時，使用者已經移出拖放區才放開，不派。
 */
export function dropDeptOf({ canceled, targetDept, targetIsCurrent, releasedOverDept }: DragRelease): AssignDept | undefined {
  if (canceled) return undefined;
  if (releasedOverDept) return releasedOverDept;
  return targetIsCurrent ? targetDept : undefined;
}
