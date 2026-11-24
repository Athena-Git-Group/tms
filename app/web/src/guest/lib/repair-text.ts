/** 補充說明最多幾個字，以字元計（一個表情符號算一個字）。 */
export const NOTE_MAX = 200;

/**
 * 六顆類別。膠囊上的字跟著介面語言（`guest.repair.category.{id}`）；送出的文字一律用繁體中文的類別，
 * 櫃檯與同仁讀的是中文，住客的介面語言另外帶給後端。
 */
export const REPAIR_CATEGORIES = [
  { id: 'ac', text: '冷氣不冷' },
  { id: 'tv', text: '電視無訊號' },
  { id: 'light', text: '燈具故障' },
  { id: 'hotWater', text: '熱水不熱' },
  { id: 'network', text: '網路連不上' },
  { id: 'other', text: '其他' },
] as const;

export type RepairCategory = (typeof REPAIR_CATEGORIES)[number];

export function noteLength(note: string): number {
  return Array.from(note).length;
}

/** 超過上限的字進不去：只留前 200 個字。 */
export function clampNote(note: string): string {
  return noteLength(note) <= NOTE_MAX ? note : Array.from(note).slice(0, NOTE_MAX).join('');
}

/** 選了類別才能送；「其他」還要有去掉頭尾空白後不是空的補充說明。 */
export function canSubmitRepair(category: RepairCategory | null, note: string): boolean {
  if (category === null) return false;
  return category.id !== 'other' || note.trim() !== '';
}

/** 送出的文字：`{類別}`、`{類別}：{補充說明}`，類別是「其他」時只有補充說明；補充說明去掉頭尾空白。 */
export function composeRepairText(category: RepairCategory, note: string): string {
  const trimmed = note.trim();
  if (category.id === 'other') return trimmed;
  return trimmed === '' ? category.text : `${category.text}：${trimmed}`;
}
