import { describe, expect, it } from 'vitest';
import { en } from './en';
import { ja } from './ja';
import { vi as vietnamese } from './vi';
import { zhTW } from './zh-TW';

function leaves(node: object, prefix = ''): [string, string][] {
  return Object.entries(node).flatMap(([key, value]): [string, string][] =>
    typeof value === 'object' && value !== null ? leaves(value, `${prefix}${key}.`) : [[`${prefix}${key}`, String(value)]],
  );
}

function hasWords(text: string): boolean {
  return /\p{L}/u.test(text.replace(/\{\{[^}]*\}\}/g, ''));
}

const chinese = new Map(leaves(zhTW.guest));

describe('住客介面的譯文', () => {
  it.each([
    ['en', en],
    ['ja', ja],
    ['vi', vietnamese],
  ] as const)('%s 的 guest 子樹沒有拿繁體中文當占位的值', (_name, locale) => {
    const placeholders = leaves(locale.guest ?? {})
      .filter(([key, value]) => hasWords(value) && chinese.get(key) === value)
      .map(([key]) => key);
    expect(placeholders).toEqual([]);
  });
});
