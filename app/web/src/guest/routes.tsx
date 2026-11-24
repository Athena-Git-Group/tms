import { Route, Routes, ScrollRestoration, useParams } from 'react-router';
import { GuestEntry } from './GuestEntry';
import { GuidePage } from './pages/GuidePage';
import { HomePage } from './pages/HomePage';
import { RepairPage } from './pages/RepairPage';
import { RequestPage } from './pages/RequestPage';
import { RequestsPage } from './pages/RequestsPage';
import { SentPage } from './pages/SentPage';
import { SupplyPage } from './pages/SupplyPage';
import { GUEST_PATHS, guestPath } from './paths';

export function Component() {
  const qrToken = useParams().qrToken ?? '';
  const homePath = guestPath(qrToken);
  return (
    <GuestEntry key={qrToken} qrToken={qrToken}>
      <ScrollRestoration getKey={(location) => (location.pathname === homePath ? location.pathname : location.key)} />
      <Routes>
        <Route index element={<HomePage />} />
        <Route path={GUEST_PATHS.supply} element={<SupplyPage />} />
        <Route path={GUEST_PATHS.repair} element={<RepairPage />} />
        <Route path={GUEST_PATHS.sent} element={<SentPage />} />
        <Route path={GUEST_PATHS.requests} element={<RequestsPage />} />
        <Route path={GUEST_PATHS.request} element={<RequestPage />} />
        <Route path={GUEST_PATHS.guide} element={<GuidePage />} />
      </Routes>
    </GuestEntry>
  );
}
