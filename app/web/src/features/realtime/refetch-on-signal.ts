import { hashKey, type QueryClient } from '@tanstack/react-query';
import { deskBoardQuery } from '../../api/desk';

/**
 * 看板讀取走到哪一步。`reading-and-dirty` 是正在讀的期間又收到單變了的訊號，
 * 正在讀的那一次可能是變化之前送出的，讀完要再讀一次。
 */
type BoardReadState = 'idle' | 'reading' | 'reading-and-dirty';

/**
 * 為什麼要整份重讀看板。
 * - `stale`：看板可能少了變化（訊號沒帶卡片或看不懂、步數跳號、營業日換了），要有一次在這之後才開始的讀取。
 * - `reconnected`：剛連上、重新連上（`resync`）或後端離開降級（`recovered`），連線以外或降級期間的變化不會補送。
 */
export type BoardRereadReason = 'stale' | 'reconnected';

/**
 * 整份重讀看板的唯一入口，只決定讀不讀、什麼時候讀；哪個訊號算哪一種原因由呼叫端決定。
 * 每條連線建一個，回傳要求重讀時呼叫的函式。
 *
 * 保證同一個分頁同時最多只有一次看板讀取在路上，而且每次 `stale` 之後一定有一次在它之後才開始的讀取。
 * 做法是正在讀時不另外開讀，只記下讀完要再讀一次；連續來二十次，也只在讀完後多讀一次。
 * 若改成每次都取消重來，一陣連續變化會讓每個瀏覽器把整份看板讀上好幾次，後端跟著被拖慢。
 * 外框的定時重抓、換回視窗與網路恢復時的重抓，遇到正在讀也只會等那一次讀完，不會另開一次。
 *
 * `reconnected` 時看板通常正在第一次讀或剛讀完，正在讀時就讓那一次讀完，不另外再讀。
 * 看板正處於讀取失敗時也不重讀，讓讀取失敗畫面照外框的規則每隔 `REREAD_INTERVAL_MS` 再試，使用者看到的再試間隔才一致。
 */
export function createBoardRereader(queryClient: QueryClient): (reason: BoardRereadReason) => void {
  let state: BoardReadState = 'idle';
  const boardHash = hashKey(deskBoardQuery.queryKey);

  // 正在讀時呼叫只會接上那一次，等它讀完才往下走，不會另開一次讀取。
  function read(next: Exclude<BoardReadState, 'idle'>): void {
    state = next;
    // 被取消、還原成讀之前的樣子時，這一次讀取照樣算完成，只能從快取有沒有收到後端的回應分辨；
    // 換上送來的卡片或派單先畫的樣子是手動寫入，不算讀完。
    let answered = false;
    const stopWatching = queryClient.getQueryCache().subscribe((event) => {
      if (event.type !== 'updated' || event.query.queryHash !== boardHash) return;
      if ((event.action.type === 'success' && !event.action.manual) || event.action.type === 'error') answered = true;
    });
    void queryClient
      .invalidateQueries({ queryKey: deskBoardQuery.queryKey }, { cancelRefetch: false })
      .then(() => {
        stopWatching();
        // 讀取被取消時不補讀：取消讀取的是派單，它要先畫好派出去的樣子，
        // 這時補讀會趕在派單送出前開始，讀回來的舊看板把單蓋回待派單。派單有結果後自己會重讀，那一次在訊號之後才開始。
        if (state === 'reading-and-dirty' && answered) read('reading');
        else state = 'idle';
      });
  }

  return (reason) => {
    const board = queryClient.getQueryState(deskBoardQuery.queryKey);
    if (reason === 'stale') {
      if (state !== 'idle') state = 'reading-and-dirty';
      // 外框的定時重抓或第一次載入可能已經在讀，那一次可能在變化之前送出，接上它之後還要再讀一次。
      else read(board === undefined || board.fetchStatus === 'idle' ? 'reading' : 'reading-and-dirty');
      return;
    }
    if (state === 'idle' && board?.status === 'success') read('reading');
  };
}
