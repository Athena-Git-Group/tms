import { describe, expect, it } from 'vitest';
import { en } from './en';
import { ja } from './ja';
import { vi as vietnamese } from './vi';
import { zhTW } from './zh-TW';

/** 型別只保證鍵一致；漏譯成空字串、插值名打錯要在執行期比對。 */
const LOCALES = { 'zh-TW': zhTW, en, ja, vi: vietnamese } as const;

function flatten(node: unknown, prefix = ''): Map<string, unknown> {
  const out = new Map<string, unknown>();
  if (node !== null && typeof node === 'object') {
    for (const [key, value] of Object.entries(node)) {
      const path = prefix === '' ? key : `${prefix}.${key}`;
      if (value !== null && typeof value === 'object') {
        for (const [p, v] of flatten(value, path)) out.set(p, v);
      } else {
        out.set(path, value);
      }
    }
  }
  return out;
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{\{\s*([^,}\s]+)[^}]*\}\}/g)].map((m) => m[1] as string).sort();
}

const flat = Object.entries(LOCALES).map(([name, tree]) => [name, flatten(tree)] as const);
const base = flatten(zhTW);

describe('介面資源檔', () => {
  it.each(flat)('%s 的鍵路徑集合和繁體中文完全相同', (_name, map) => {
    expect([...map.keys()].sort()).toEqual([...base.keys()].sort());
  });

  it.each(flat)('%s 的每個值都是非空字串', (_name, map) => {
    const bad = [...map].filter(([, v]) => typeof v !== 'string' || v.trim() === '').map(([k]) => k);
    expect(bad).toEqual([]);
  });

  it.each(flat)('%s 的插值變數名稱和繁體中文一致', (_name, map) => {
    const mismatched = [...base.keys()].filter((key) => {
      const a = placeholders(String(base.get(key)));
      const b = placeholders(String(map.get(key) ?? ''));
      return a.join('|') !== b.join('|');
    });
    expect(mismatched).toEqual([]);
  });
});
