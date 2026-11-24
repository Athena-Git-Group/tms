import type { AcceptedVia, DeskTask, Dept, InspectionResult, TaskSource, TaskState, TaskType } from './types';

const TASK_STATES = [
  'triage',
  'pool',
  'accepted',
  'doing',
  'done',
  'inspected',
  'closed',
  'deferred',
  'interrupted',
  'cancelled',
] as const satisfies readonly TaskState[];
const DEPTS = ['housekeeping', 'engineering', 'frontdesk'] as const satisfies readonly Dept[];
const TASK_TYPES = ['amenity', 'repair', 'cleaning', 'other'] as const satisfies readonly TaskType[];
const TASK_SOURCES = ['guest', 'desk', 'system', 'staff'] as const satisfies readonly TaskSource[];
const ACCEPTED_VIAS = [
  'plan_assigned',
  'self_claimed',
  'manager_assigned',
  'reassigned',
] as const satisfies readonly AcceptedVia[];
const INSPECTION_RESULTS = ['passed', 'waived', 'failed'] as const satisfies readonly InspectionResult[];

type Fields = Record<string, unknown>;

function isFields(value: unknown): value is Fields {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isOneOf<T extends string>(values: readonly T[], value: unknown): value is T {
  return (values as readonly unknown[]).includes(value);
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value);
}

function isAbsentOr(value: unknown, check: (present: unknown) => boolean): boolean {
  return value === undefined || value === null || check(value);
}

function isText(value: unknown): value is string {
  return typeof value === 'string';
}

function isGuestText(value: unknown): boolean {
  return (
    isFields(value) && isText(value.original) && isAbsentOr(value.lang, isText) && isAbsentOr(value.translated, isText)
  );
}

function isSop(value: unknown): boolean {
  return isFields(value) && isInteger(value.done) && isInteger(value.total);
}

function isInterruption(value: unknown): boolean {
  return (
    isFields(value) &&
    typeof value.bySelf === 'boolean' &&
    isAbsentOr(value.reason, isText) &&
    isAbsentOr(value.handoverNote, isText)
  );
}

function isDeferral(value: unknown): boolean {
  return (
    isFields(value) &&
    typeof value.bySelf === 'boolean' &&
    isAbsentOr(value.byName, isText) &&
    isAbsentOr(value.reason, isText)
  );
}

export function isDeskTask(value: unknown): value is DeskTask {
  if (!isFields(value)) return false;
  return (
    isText(value.id) &&
    isInteger(value.seq) &&
    isText(value.displayNo) &&
    isOneOf(TASK_STATES, value.state) &&
    typeof value.answered === 'boolean' &&
    isOneOf(DEPTS, value.dept) &&
    isOneOf(TASK_TYPES, value.type) &&
    isOneOf(TASK_SOURCES, value.source) &&
    isText(value.roomNo) &&
    isInteger(value.floor) &&
    typeof value.vip === 'boolean' &&
    isAbsentOr(value.guestName, isText) &&
    isText(value.title) &&
    typeof value.guestTyped === 'boolean' &&
    isAbsentOr(value.guestText, isGuestText) &&
    isAbsentOr(value.assigneeId, isText) &&
    isAbsentOr(value.assigneeName, isText) &&
    isText(value.createdAt) &&
    isAbsentOr(value.waitingSince, isText) &&
    isAbsentOr(value.acceptedAt, isText) &&
    isAbsentOr(value.acceptedVia, (via) => isOneOf(ACCEPTED_VIAS, via)) &&
    isAbsentOr(value.startedAt, isText) &&
    isAbsentOr(value.doneAt, isText) &&
    isAbsentOr(value.interruptedAt, isText) &&
    isAbsentOr(value.endedAt, isText) &&
    isText(value.updatedAt) &&
    isAbsentOr(value.inspection, (result) => isOneOf(INSPECTION_RESULTS, result)) &&
    isAbsentOr(value.sop, isSop) &&
    isAbsentOr(value.interruption, isInterruption) &&
    isAbsentOr(value.deferral, isDeferral)
  );
}
