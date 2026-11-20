import type { GuestInfoPage } from '../api/types';

/** 列得出來的資訊頁一定有標題。 */
export type ListedInfoPage = GuestInfoPage & { title: string };

/** 後端照飯店設定的順序原樣回，標題空白的也在裡面；標題是空的或只有空白就不列。 */
export function listableInfoPages(pages: readonly GuestInfoPage[]): ListedInfoPage[] {
  return pages.filter((page): page is ListedInfoPage => (page.title ?? '').trim() !== '');
}
