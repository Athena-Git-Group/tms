export type StaffRole =
  | 'frontdesk'
  | 'housekeeping'
  | 'engineering'
  | 'manager'
  | 'duty_manager'
  | 'owner';

export type Dept = 'housekeeping' | 'engineering' | 'frontdesk';

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: { field?: string; issue?: string }[];
  context?: Record<string, unknown>;
}

export interface LoginRequest {
  loginId: string;
  password: string;
  client?: 'frontdesk_web';
  hotelCode?: string;
}

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
  type: string;
  actor: {
    kind: 'guest' | 'system' | 'staff';
    role?: Exclude<StaffRole, 'owner'> | null;
    name?: string | null;
  };
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
