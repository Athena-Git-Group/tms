import type { DeskTask } from '../api/types';
import type { BoardColumn } from './board-columns';
import { columnOf } from './board-columns';
import type { LaneKind } from './board-view-model';
import type { AssignDept } from './dispatch';

/** 拖放區由上到下的順序：房務在上、工務在下，照雛形 `ui/desk.js` 的 `dragZones`。 */
const ZONE_ORDER: readonly AssignDept[] = ['housekeeping', 'engineering'];

/** 只有「待派單」欄的卡片拖得動，其他欄的卡片只能點開來看。 */
export function canDragToDispatch(task: DeskTask): boolean {
  return columnOf(task) === 'triage';
}

/**
 * 正在拖這張單時，看板上這一格要出現哪幾個拖放區（由上到下）。拖放區只出現在「待認領」欄：
 * 不分泳道時兩個都出現；部門泳道時「給房務」只在房務那一列、「給工務」只在工務那一列，「未分部門」那一列沒有；
 * 樓層泳道時兩個都只出現在這張單那一層樓的那一列。
 *
 * @param lane 這一格所在的泳道；不分泳道時是 `null`
 */
export function dropZoneDepts(column: BoardColumn, lane: LaneKind | null, dragging: DeskTask): AssignDept[] {
  if (column !== 'pool') return [];
  if (lane === null) return [...ZONE_ORDER];
  if (lane.kind === 'dept') return ZONE_ORDER.filter((dept) => dept === lane.dept);
  return lane.floor === dragging.floor ? [...ZONE_ORDER] : [];
}

/** 看板上一格在拖曳中的樣子：要放哪幾個拖放區、要不要變淡。 */
export interface CellWhileDragging {
  zones: AssignDept[];
  faded: boolean;
}

/**
 * 看板上一格在拖曳中的樣子。變淡是為了讓櫃檯一眼看出能放的地方；
 * 有拖放區的格子與正在拖的那張單所在的格子不變淡，沒有人在拖時都照常。
 *
 * @param lane 這一格所在的泳道；不分泳道時是 `null`
 * @param cellTasks 這一格裡的單
 * @param dragging 正在拖的那張單；沒有人在拖時是 `null`
 */
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

/** 放開那一刻的情況，給 `dropDeptOf` 判斷要不要派、派給誰。 */
export interface DragRelease {
  /** 使用者按了 Esc 取消拖曳。 */
  canceled: boolean;
  /** 拖曳套件判斷的落點；沒有是 `undefined`。 */
  targetDept: AssignDept | undefined;
  /** 套件的落點是不是用放開那一點算的；套件還停在上一次移動的位置時是 `false`。 */
  targetIsCurrent: boolean;
  /** 放開那一點底下的拖放區（畫面上的 `data-drop-dept`）；不在拖放區上是 `undefined`。 */
  releasedOverDept: AssignDept | undefined;
}

/**
 * 放開之後要派給哪個部門；不派時是 `undefined`。
 *
 * 取消要最先看：按 Esc 時套件可能還記著最後滑過的拖放區，晚一步看就會把取消當成派單。
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
