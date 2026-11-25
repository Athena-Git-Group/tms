import type { StaffRole } from '../api/types';
import { PATHS, type AppPath } from './paths';

export type RouteArea = 'login' | 'changePassword' | 'shell' | 'settings';

export type AccessSession = { role: StaffRole; mustChangePassword: boolean };

export type RouteDecision = { allow: true } | { allow: false; redirectTo: AppPath };

export const DESK_ROLES: readonly StaffRole[] = ['frontdesk', 'manager', 'duty_manager'];

export function canUseDesk(role: StaffRole): boolean {
  return DESK_ROLES.includes(role);
}

const SETTINGS_ROLES: readonly StaffRole[] = ['manager', 'duty_manager'];

export function canChangeSettings(role: StaffRole): boolean {
  return SETTINGS_ROLES.includes(role);
}

const ALLOW: RouteDecision = { allow: true };

function onlyIn(allowed: RouteArea, allowedPath: AppPath, current: RouteArea): RouteDecision {
  return current === allowed ? ALLOW : { allow: false, redirectTo: allowedPath };
}

export function sessionAtHotel<S extends { hotelCode: string }>(session: S | null, hotelCode: string): S | null {
  return session !== null && session.hotelCode === hotelCode ? session : null;
}

export function decideRoute(area: RouteArea, session: AccessSession | null): RouteDecision {
  if (session === null) return onlyIn('login', PATHS.login, area);
  if (session.mustChangePassword) return onlyIn('changePassword', PATHS.changePassword, area);
  if (!canUseDesk(session.role)) return onlyIn('login', PATHS.login, area);
  if (area === 'settings') return canChangeSettings(session.role) ? ALLOW : { allow: false, redirectTo: PATHS.board };
  return onlyIn('shell', PATHS.board, area);
}
