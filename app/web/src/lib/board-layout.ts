/**
 * 看板本體的尺寸，照 `ui/ui-plan.md`「版面尺寸與伸縮規則」：8 欄都在時每欄至少 200px、沒有上限，平均分掉可用寬度；
 * 數量卡篩選後欄數變少時每欄 200 至 260px、靠左；欄距 10px，第一欄與最後一欄離內容區左右各 16px。
 * 這幾個數字只寫在 `index.css` 的版面變數（`--min-width-board-column`、`--max-width-board-column-filtered`、
 * `--gap-board`、`--spacing-gutter`），這裡用 `var(...)` 讀，畫面上用對應的 Tailwind 名稱。
 *
 * 看板本體（可以捲動的那一塊）左右貼齊內容區，16px 的邊距放在捲動的內容裡面：
 * 沒捲動時左邊留 16px，捲到最右邊時右邊留 16px，視窗夠寬、不用捲時兩邊各 16px。
 * 這樣 1280 寬、側欄收起時看得到 6 欄（16 + 6 × 200 + 5 × 10 = 1266 ≤ 1280），和畫面規劃的欄數表一致。
 */

/** 看板全部的欄數；少於這個數就是數量卡篩選過。 */
const ALL_COLUMNS = 8;

/** 量位置時小數捨入可能差到 1px，欄的右緣超出看得到的範圍不到這麼多，仍算看得到。 */
const EDGE_TOLERANCE_PX = 1;

/** 放各欄的那一層要多寬：至少是可用寬度，欄數多到放不下時撐開成每欄最窄寬度加上欄距與邊距，看板就可以橫向捲動。 */
export function boardContentWidth(columns: number): string {
  const gaps = Math.max(0, columns - 1);
  return `max(100%, calc(${columns} * var(--min-width-board-column) + ${gaps} * var(--gap-board) + 2 * var(--spacing-gutter)))`;
}

/** 一欄（欄頭、格子）寬度限制的 Tailwind 名稱：都有最窄寬度，數量卡篩選過才有最寬寬度。欄本身平均分掉多出來的寬度。 */
export function boardColumnWidth(columns: number): string {
  return columns < ALL_COLUMNS ? 'min-w-board-column max-w-board-column-filtered' : 'min-w-board-column';
}

/**
 * 看板右邊看不到的欄數：欄頭的右緣超出看得到範圍右緣的欄，只露出一部分的也算看不到。
 *
 * @param headRights 各欄欄頭右緣的視窗座標
 * @param visibleRight 看板看得到範圍的右緣（扣掉直向捲軸）的視窗座標
 */
export function columnsHiddenOnRight(headRights: readonly number[], visibleRight: number): number {
  return headRights.filter((right) => right > visibleRight + EDGE_TOLERANCE_PX).length;
}
