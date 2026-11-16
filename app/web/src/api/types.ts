/**
 * 後端契約的型別，照 `specs/truth/contracts/` 寫，欄位名稱與值都和契約一致。
 * 契約裡「可為空」的欄位寫成 `| null`；契約沒有列在 required 的欄位寫成可省略（`?`）。
 * 契約改了就改這裡，畫面不自己另外定義一份，免得兩邊對不起來。
 */

export type StaffRole =
  | 'frontdesk'
  | 'housekeeping'
  | 'engineering'
  | 'manager'
  | 'duty_manager'
  | 'owner';

/** 待派單與當場回答的單還沒有分給房務或工務，部門是 `frontdesk`。 */
export type Dept = 'housekeeping' | 'engineering' | 'frontdesk';

/** 契約 `shared.yaml` 的 `Error`。 */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: { field?: string; issue?: string }[];
  /** 內容依 `code` 而定，例如 `task_not_in_triage` 帶 `resolution`、`roomNo`。 */
  context?: Record<string, unknown>;
}

export interface LoginRequest {
  loginId: string;
  password: string;
  /** 櫃檯 Web 一律帶 `frontdesk_web`，後端才會擋下不能用櫃檯畫面的角色。 */
  client?: 'frontdesk_web';
}

/** 登入與設定新密碼成功時後端回的是同一個形狀；畫面把它整份存在這個分頁當登入狀態。 */
export interface LoginResponse {
  token: string;
  expiresAt: string;
  staffId: string;
  name: string;
  loginId: string;
  hotelName: string;
  dept?: Dept | null;
  role: StaffRole;
  mustChangePassword: boolean;
}

export interface ChangePasswordRequest {
  currentPassword?: string | null;
  newPassword: string;
}

export interface PublicHotel {
  name: string;
}

export type TaskState =
  | 'triage'
  | 'pool'
  | 'accepted'
  | 'doing'
  | 'done'
  | 'inspected'
  | 'closed'
  | 'deferred'
  | 'interrupted'
  | 'cancelled';

export type TaskType = 'amenity' | 'repair' | 'cleaning' | 'other';

export type TaskSource = 'guest' | 'desk' | 'system' | 'staff';

export type AcceptedVia = 'plan_assigned' | 'self_claimed' | 'manager_assigned' | 'reassigned';

export type InspectionResult = 'passed' | 'waived' | 'failed';

export type CancelReason = 'guest_withdrew' | 'manager_void' | 'plan_superseded' | 'staff_withdrew';

export type RepairResolutionType = 'fixed' | 'temporary' | 'cannot_fix' | 'no_action_needed';

/** 總覽看板、待派單頁、徽章與數量卡都從這一份算，畫面上的數字才會彼此對得起來。 */
export interface DeskBoard {
  serverTime: string;
  businessDate: string;
  floors: number[];
  tasks: DeskTask[];
  myToday: DeskMyToday;
}

export interface DeskMyToday {
  assignedCount: number;
  answeredCount: number;
}

export interface DeskGuestText {
  original: string;
  lang?: string | null;
  translated?: string | null;
}

/** 時間欄位都是時刻；「等 N 分」「接 N 分」由畫面用校正過的現在去換算。 */
export interface DeskTask {
  id: string;
  displayNo: string;
  state: TaskState;
  answered: boolean;
  dept: Dept;
  type: TaskType;
  source: TaskSource;
  roomNo: string;
  floor: number;
  vip: boolean;
  guestName?: string | null;
  title: string;
  guestTyped: boolean;
  guestText?: DeskGuestText | null;
  assigneeId?: string | null;
  assigneeName?: string | null;
  createdAt: string;
  waitingSince?: string | null;
  acceptedAt?: string | null;
  acceptedVia?: AcceptedVia | null;
  startedAt?: string | null;
  doneAt?: string | null;
  interruptedAt?: string | null;
  endedAt?: string | null;
  updatedAt: string;
  inspection?: InspectionResult | null;
  sop?: { done: number; total: number } | null;
  interruption?: {
    bySelf: boolean;
    reason?: string | null;
    handoverNote?: string | null;
  } | null;
  deferral?: {
    bySelf: boolean;
    byName?: string | null;
    reason?: string | null;
  } | null;
}

export interface DeskTaskDetail extends DeskTask {
  serverTime: string;
  answer?: string | null;
  answeredByName?: string | null;
  guest?: { name: string; checkinDate: string; checkoutDate: string } | null;
  createdByName?: string | null;
  accepted?: {
    via: AcceptedVia;
    at: string;
    byName?: string | null;
    previousAssigneeName?: string | null;
  } | null;
  cancellation?: { reason: CancelReason; byName?: string | null } | null;
  repair?: {
    resolutionType?: RepairResolutionType | null;
    verifiedByName?: string | null;
  } | null;
  timeline: DeskTimelineEntry[];
}

export interface DeskTimelineEntry {
  seq: number;
  at: string;
  /** 值同 `task_events.type`（例如 `created`、`triaged`、`answered`）；契約沒有列舉，所以是字串。 */
  type: string;
  actor: {
    kind: 'guest' | 'system' | 'staff';
    role?: Exclude<StaffRole, 'owner'> | null;
    name?: string | null;
  };
  /** 後端依事件種類只帶其中幾項，畫面拿它們組歷程的句子。 */
  detail?: {
    dept?: Dept;
    initialState?: 'triage' | 'pool' | 'accepted';
    guestTyped?: boolean;
    ruleLabel?: string | null;
    via?: 'button' | 'drag';
    answer?: string;
    assigneeName?: string | null;
    previousAssigneeName?: string | null;
    reason?: string | null;
    bySelf?: boolean;
    note?: string | null;
    cancelReason?: CancelReason;
    resolutionType?: RepairResolutionType;
    rating?: number;
  };
}

export interface DeskQuickReply {
  key: string;
  title: string;
  body: string;
}

export interface DeskAssignRequest {
  dept: 'housekeeping' | 'engineering';
  via?: 'button' | 'drag';
}

export interface DeskAnswerRequest {
  answer: string;
}
