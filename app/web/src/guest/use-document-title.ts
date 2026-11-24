import { useEffect } from 'react';

/** 瀏覽器分頁上的字跟著目前這一頁與介面語言。 */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title;
  }, [title]);
}
