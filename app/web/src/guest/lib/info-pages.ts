import type { GuestInfoPage } from '../api/types';

export type ListedInfoPage = GuestInfoPage & { title: string };

export function listableInfoPages(pages: readonly GuestInfoPage[]): ListedInfoPage[] {
  return pages.filter((page): page is ListedInfoPage => (page.title ?? '').trim() !== '');
}
