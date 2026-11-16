import { QueryClient } from '@tanstack/react-query';

/**
 * 整個應用程式共用的一份後端資料快取。
 * 什麼時候重抓、失敗要不要重試，由用到資料的功能在各自的查詢上設定，這裡不放預設規則。
 */
export const queryClient = new QueryClient();
