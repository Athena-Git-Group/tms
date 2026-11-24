/** 送出時間以瀏覽器時區的 24 小時制「時:分」顯示，不帶日期。 */
export function hhmm(createdAt: string): string {
  const at = new Date(createdAt);
  return `${String(at.getHours()).padStart(2, '0')}:${String(at.getMinutes()).padStart(2, '0')}`;
}
