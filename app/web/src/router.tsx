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

/**
 * 每一條路徑都包在這一層裡：依登入狀態問 `decideRoute` 能不能進，不能進就換到它指的那一頁。
 * 登入狀態一變（登入、登出、設定完新密碼）這一層就重新判斷一次，
 * 所以各畫面只要更新登入狀態，不需要自己再寫要換去哪一頁。
 * 換頁時沿用目前網址上的飯店識別代碼，登入前後、被帶回登入頁時都留在同一家飯店底下。
 * 飯店之間是隔離的：這個分頁登入的是別家時，在這一家當作沒有登入，會被帶到這一家的登入頁。
 */
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

/** 認不得的網址先帶去同一家飯店的總覽看板，再由上面那一層依登入狀態決定實際落在哪一頁。 */
function ToBoard() {
  return <Navigate to={hotelPath(useHotelCode(), PATHS.board)} replace />;
}

/** 四條路徑都掛在飯店識別代碼底下，網址長成 `/athena/login`、`/athena/board`。 */
const base = `/:${HOTEL_CODE_PARAM}`;

/**
 * 沒有飯店識別代碼的網址：根網址，以及直接打四條路徑本身（`/login`、`/board/...` 等）。
 * 這些都顯示「輸入飯店代碼」頁，輸入後帶去那一家的登入頁；這裡本身不提供登入。
 * 固定的路徑比帶參數的路徑優先比對，所以 `/login` 不會被當成代碼是 `login` 的飯店；
 * 這四個字本來就是後端的保留字，不會有飯店用它們當代碼。
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
