import type { Dept, TaskState, TaskType } from '../../api/types';
import type { Language } from '../../i18n/languages';

export type { Dept, TaskType };

/** 需求的十個狀態值，與櫃檯共用同一組字面值，住客頁經狀態對照表換成看得懂的階段。 */
export type RequestState = TaskState;

export interface GuestInfoPage {
  key: string;
  title: string | null;
  body: string | null;
}

export interface GuestMenuItem {
  key: string;
  label: string | null;
}

export interface GuestHome {
  infoPages: GuestInfoPage[];
  menu: GuestMenuItem[];
}

export interface ScanResponse extends GuestHome {
  sessionToken: string;
  expiresAt: string;
  roomNo: string;
}

export interface GuestTask {
  id: string;
  type: TaskType;
  state: RequestState;
  /** 待派單時是 `frontdesk`；後端沒給時畫面也當櫃檯。 */
  dept: Dept | null;
  title: string | null;
  /** 櫃檯當場回答的全文；有值就是當場回答結案的單。 */
  answer: string | null;
  rating: number | null;
  createdAt: string;
}

/** 後端一次只收一項，而且服務項目與打字兩者剛好擇一，兩個都送會被拒絕。 */
export type CreateRequestBody =
  | { itemId: string; quantity: number; text?: never; lang?: never }
  | { text: string; lang: Language; itemId?: never; quantity?: never };

export interface RatingBody {
  rating: number;
}

/** 住客串流的訊號只是「有東西變了」，不帶需求內容，收到後一律重讀。 */
export type GuestStreamEvent =
  | { event: 'init'; data: { stream_pos: number } }
  | { event: 'resync'; data: { reason: string; refetch: string } }
  | {
      event: 'task';
      data: { stream_pos: number; task_id: string; seq: number; type: string; occurred_at?: string };
    }
  | { event: 'heartbeat'; data: { stream_pos: number; degraded?: boolean } }
  | { event: 'degraded'; data: { reason: string } };
