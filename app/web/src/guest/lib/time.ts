export function hhmm(createdAt: string): string {
  const at = new Date(createdAt);
  return `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`;
}
