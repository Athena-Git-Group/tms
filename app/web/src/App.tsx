import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';
import { queryClient } from './api/query-client';
import './i18n';
import { router } from './router';

/** 應用程式的根元件：接上後端資料的快取、多語系與四條路徑。 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
