import type { Dept, TaskState, TaskType } from '../../api/types';
import type { Language } from '../../i18n/languages';

export type { Dept, TaskType };

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
  dept: Dept | null;
  title: string | null;
  answer: string | null;
  rating: number | null;
  createdAt: string;
}

export interface GuestQuota {
  limit: number;
  remaining: number;
  resetSeconds: number;
}

export interface GuestRequestList {
  requests: GuestTask[];
  quota: GuestQuota | null;
}

export type CreateRequestBody =
  | { itemId: string; quantity: number; text?: never; lang?: never }
  | { text: string; lang: Language; itemId?: never; quantity?: never };

export interface RatingBody {
  rating: number;
}

export type GuestStreamEvent =
  | { event: 'init'; data: { stream_pos: number } }
  | { event: 'resync'; data: { reason: string; refetch: string } }
  | {
      event: 'task';
      data: { stream_pos: number; task_id: string; seq: number; type: string; occurred_at?: string };
    }
  | { event: 'heartbeat'; data: { stream_pos: number; degraded: boolean } }
  | { event: 'degraded'; data: { reason: 'kafka_consumer_lag' | 'kafka_unavailable' } }
  | { event: 'recovered'; data: Record<string, never> };
