export type GreetingKey = 'guest.greeting.morning' | 'guest.greeting.afternoon' | 'guest.greeting.evening';

/** 手機時間 11:00 前、18:00 前、其餘三段。 */
export function greetingKey(now: Date): GreetingKey {
  const hour = now.getHours();
  if (hour < 11) return 'guest.greeting.morning';
  if (hour < 18) return 'guest.greeting.afternoon';
  return 'guest.greeting.evening';
}

/** 估一行字有幾個字寬：中日文字算 1、空白 0.3、其餘 0.58，照原型的估法。 */
function emWidth(text: string): number {
  return Array.from(text).reduce((sum, ch) => sum + (ch === ' ' ? 0.3 : ch.charCodeAt(0) > 0x2e7f ? 1 : 0.58), 0);
}

/**
 * 三種問候與第二行裡最長的一行估寬超過 10 個字寬，整組問候改小一級。
 * 看的是三個時段一起，換時段時字級不會跳。
 */
export function greetingIsCompact(lines: readonly string[]): boolean {
  return Math.max(...lines.map(emWidth)) > 10;
}
