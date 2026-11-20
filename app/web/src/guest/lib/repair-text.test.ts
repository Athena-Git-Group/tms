import { describe, expect, it } from 'vitest';
import {
  NOTE_MAX,
  REPAIR_CATEGORIES,
  canSubmitRepair,
  clampNote,
  composeRepairText,
  noteLength,
  type RepairCategory,
} from './repair-text';

const category = (text: string) => REPAIR_CATEGORIES.find((c) => c.text === text) as RepairCategory;

describe('composeRepairText', () => {
  it('只有類別', () => {
    expect(composeRepairText(category('冷氣不冷'), '')).toBe('冷氣不冷');
  });

  it('類別加補充說明', () => {
    expect(composeRepairText(category('冷氣不冷'), '風量很小')).toBe('冷氣不冷：風量很小');
  });

  it('「其他」只有補充說明', () => {
    expect(composeRepairText(category('其他'), '浴室排水很慢')).toBe('浴室排水很慢');
  });

  it('補充說明頭尾的空白去掉，只有空白就當沒寫', () => {
    expect(composeRepairText(category('熱水不熱'), '  水是溫的 \n')).toBe('熱水不熱：水是溫的');
    expect(composeRepairText(category('熱水不熱'), '   ')).toBe('熱水不熱');
  });
});

describe('clampNote', () => {
  it('以字元計，表情符號算一個字', () => {
    expect(noteLength('🙂多')).toBe(2);
    const full = '🙂'.repeat(NOTE_MAX);
    expect(clampNote(full + '多')).toBe(full);
  });

  it('沒超過就原樣', () => {
    expect(clampNote('風量很小')).toBe('風量很小');
  });
});

describe('canSubmitRepair', () => {
  it('沒選類別不能送，打了字也一樣', () => {
    expect(canSubmitRepair(null, '浴室排水很慢')).toBe(false);
  });

  it('選了一般類別就能送，補充說明可以空著', () => {
    expect(canSubmitRepair(category('冷氣不冷'), '')).toBe(true);
  });

  it.each([
    ['', false],
    ['   ', false],
    ['浴室排水很慢', true],
  ])('「其他」的補充說明是「%s」時能不能送：%s', (note, expected) => {
    expect(canSubmitRepair(category('其他'), note)).toBe(expected);
  });
});
