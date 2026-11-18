export function sidebarOpen(page: string, sidebarOpenByUser: boolean | null): boolean {
  return sidebarOpenByUser ?? page === 'triage';
}
