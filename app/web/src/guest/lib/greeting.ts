export type GreetingKey = 'guest.greeting.morning' | 'guest.greeting.afternoon' | 'guest.greeting.evening';

export function greetingKey(now: Date): GreetingKey {
  const hour = now.getHours();
  if (hour < 11) return 'guest.greeting.morning';
  if (hour < 18) return 'guest.greeting.afternoon';
  return 'guest.greeting.evening';
}

function emWidth(text: string): number {
  return Array.from(text).reduce((sum, ch) => sum + (ch === ' ' ? 0.3 : ch.charCodeAt(0) > 0x2e7f ? 1 : 0.58), 0);
}

export function greetingIsCompact(lines: readonly string[]): boolean {
  return Math.max(...lines.map(emWidth)) > 10;
}
