import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';
import { PATHS } from './lib/paths';
import { decideRoute, type RouteArea } from './lib/route-access';
import { BoardPage } from './pages/board/BoardPage';
import { ChangePasswordPage } from './pages/change-password/ChangePasswordPage';
import { LoginPage } from './pages/login/LoginPage';
import { TriagePage } from './pages/triage/TriagePage';
import { DeskShell } from './shell/DeskShell';
import { useSessionStore } from './stores/session';

/**
 * 每一條路徑都包在這一層裡：依登入狀態問 `decideRoute` 能不能進，不能進就換到它指的那一頁。
 * 登入狀態一變（登入、登出、設定完新密碼）這一層就重新判斷一次，
 * 所以各畫面只要更新登入狀態，不需要自己再寫要換去哪一頁。
 */
function Gate({ area, children }: { area: RouteArea; children: ReactNode }) {
  const session = useSessionStore((state) => state.session);
  const decision = decideRoute(area, session);
  if (!decision.allow) return <Navigate to={decision.redirectTo} replace />;
  return children;
}

export const router = createBrowserRouter([
  {
    path: PATHS.login,
    element: (
      <Gate area="login">
        <LoginPage />
      </Gate>
    ),
  },
  {
    path: PATHS.changePassword,
    element: (
      <Gate area="changePassword">
        <ChangePasswordPage />
      </Gate>
    ),
  },
  {
    element: (
      <Gate area="shell">
        <DeskShell />
      </Gate>
    ),
    children: [
      { path: PATHS.board, element: <BoardPage /> },
      { path: PATHS.triage, element: <TriagePage /> },
    ],
  },
  // 首頁與認不得的網址都先帶去總覽看板，再由上面那一層依登入狀態決定實際落在哪一頁。
  { path: '*', element: <Navigate to={PATHS.board} replace /> },
]);
