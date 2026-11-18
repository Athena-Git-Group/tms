const ALL_COLUMNS = 8;

const EDGE_TOLERANCE_PX = 1;

export function boardContentWidth(columns: number): string {
  const gaps = Math.max(0, columns - 1);
  return `max(100%, calc(${columns} * var(--min-width-board-column) + ${gaps} * var(--gap-board) + 2 * var(--spacing-gutter)))`;
}

export function boardColumnWidth(columns: number): string {
  return columns < ALL_COLUMNS ? 'min-w-board-column max-w-board-column-filtered' : 'min-w-board-column';
}

export function columnsHiddenOnRight(headRights: readonly number[], visibleRight: number): number {
  return headRights.filter((right) => right > visibleRight + EDGE_TOLERANCE_PX).length;
}
