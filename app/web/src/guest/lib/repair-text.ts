export const NOTE_MAX = 200;

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

export function clampNote(note: string): string {
  return noteLength(note) <= NOTE_MAX ? note : Array.from(note).slice(0, NOTE_MAX).join('');
}

export function canSubmitRepair(category: RepairCategory | null, note: string): boolean {
  if (category === null) return false;
  return category.id !== 'other' || note.trim() !== '';
}

export function composeRepairText(category: RepairCategory, note: string): string {
  const trimmed = note.trim();
  if (category.id === 'other') return trimmed;
  return trimmed === '' ? category.text : `${category.text}：${trimmed}`;
}
