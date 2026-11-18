export function clockOffsetMs(serverTime: string, deviceNow: Date): number {
  return Date.parse(serverTime) - deviceNow.getTime();
}

export function dateAtUtcOffset(instant: Date, offsetMinutes: number): string {
  return new Date(instant.getTime() + offsetMinutes * 60_000).toISOString().slice(0, 10);
}

export function correctedNow(offsetMs: number, deviceNow: Date): Date {
  return new Date(deviceNow.getTime() + offsetMs);
}

export function utcOffsetMinutes(serverTime: string): number | null {
  const match = /(?:(Z)|([+-])(\d{2}):?(\d{2}))$/i.exec(serverTime.trim());
  if (!match) {
    return null;
  }
  if (match[1]) {
    return 0;
  }
  const minutes = Number(match[3]) * 60 + Number(match[4]);
  return match[2] === '-' ? -minutes : minutes;
}
