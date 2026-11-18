import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { HOTEL_CODE_PARAM, PATHS, hotelPath } from './lib/paths';
import { useHotelCode } from './lib/use-hotel-code';
import { decideRoute, sessionAtHotel, type RouteArea } from './lib/route-access';
import { BoardPage } from './pages/board/BoardPage';
import { ChangePasswordPage } from './pages/change-password/ChangePasswordPage';
import { LoginPage } from './pages/login/LoginPage';
import { MissingHotelPage } from './pages/missing-hotel/MissingHotelPage';
import { TriagePage } from './pages/triage/TriagePage';
import { DeskShell } from './shell/DeskShell';
import { useSessionStore } from './stores/session';

function Gate({ area, children }: { area: RouteArea; children: ReactNode }) {
  const hotelCode = useHotelCode();
  const session = sessionAtHotel(
    useSessionStore((state) => state.session),
    hotelCode,
  );
  const decision = decideRoute(area, session);
  if (!decision.allow) return <Navigate to={hotelPath(hotelCode, decision.redirectTo)} replace />;
  return children;
}

function ToBoard() {
  return <Navigate to={hotelPath(useHotelCode(), PATHS.board)} replace />;
}

const base = `/:${HOTEL_CODE_PARAM}`;

/**
 * 沒帶飯店代碼的網址一律顯示缺少飯店的頁面。React Router 會先比對固定路徑，再比對帶參數的路徑，
 * 所以 `/login` 會落在這裡，不會被當成代碼是 `login` 的飯店。
 */
const withoutHotelCode: RouteObject[] = [
  { path: '/', element: <MissingHotelPage /> },
  ...Object.values(PATHS).flatMap((path) => [
    { path, element: <MissingHotelPage /> },
    { path: `${path}/*`, element: <MissingHotelPage /> },
  ]),
];

export const router = createBrowserRouter([
  ...withoutHotelCode,
  {
    path: `${base}${PATHS.login}`,
    element: (
      <Gate area="login">
        <LoginPage />
      </Gate>
    ),
  },
  {
    path: `${base}${PATHS.changePassword}`,
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
      { path: `${base}${PATHS.board}`, element: <BoardPage /> },
      { path: `${base}${PATHS.triage}`, element: <TriagePage /> },
    ],
  },
  { path: `${base}/*`, element: <ToBoard /> },
]);
