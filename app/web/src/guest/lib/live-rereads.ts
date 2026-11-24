export type GuestData = 'home' | 'requests';

const REREADS_ON: Readonly<Record<string, readonly GuestData[]>> = {
  task: ['requests'],
  resync: ['home', 'requests'],
};

export function rereadsOn(event: string): readonly GuestData[] {
  return Object.hasOwn(REREADS_ON, event) ? REREADS_ON[event]! : [];
}
