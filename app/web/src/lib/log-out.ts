import type { QueryClient } from '@tanstack/react-query';
import { useBoardDragStore } from '../stores/board-drag';
import { useBoardViewStore } from '../stores/board-view';
import { useDeskPanelsStore } from '../stores/desk-panels';
import { useDispatchStore } from '../stores/dispatch';
import { useLoginNoticeStore } from '../stores/login-notice';
import { useSessionStore } from '../stores/session';

/**
 * 離開登入狀態時清掉「屬於這個人」的東西。櫃檯的裝置是大家輪流用的，下一位不能看到上一位的任何東西。
 * 每一條離開登入狀態的路都要走這裡，下一位拿到的畫面才會一樣乾淨。
 *
 * 依序清掉：
 * 1. 看板的選擇（泳道、數量卡篩選、房號搜尋、側欄），連同分頁儲存的那一份。
 * 2. 開著的詳情面板與當場回答面板、看板上拖曳中的卡片、派單浮條與派單相關的對話框，以及抓過的後端資料（傳進來的快取整份清空）。
 * 3. 留給登入頁的提示。要替登入頁留提示的人，得在這個函式之後再留，不然會被這一步清掉。
 * 4. 最後才清登入狀態（分頁儲存的那一份）。清掉之後路由那一層會把畫面換到同一家飯店的登入頁，
 *    放在最後是為了換頁時前面幾樣已經清乾淨。
 *
 * 介面語言不清：它記在這台裝置上、是每個人各自的習慣，不跟著某一次登入走，下次登入還要用。
 *
 * 哪些狀態跟著分頁、登出要不要清，集中列在 `lib/persistence.ts`。跟著分頁的鍵都靠各自 store 的清除動作移掉，
 * 所以那邊新增一個跟著分頁的狀態，這裡也要接上它的清除；單元測試會拿那份清單檢查有沒有漏清。
 * 之後有新的「屬於這個人的狀態」（例如即時串流的連線），也要在這裡接上一起清掉。
 */
export function logOut(queryClient: QueryClient): void {
  useBoardViewStore.getState().reset();
  useDeskPanelsStore.getState().reset();
  useBoardDragStore.getState().reset();
  useDispatchStore.getState().reset();
  queryClient.clear();
  useLoginNoticeStore.getState().clear();
  useSessionStore.getState().clearSession();
}
