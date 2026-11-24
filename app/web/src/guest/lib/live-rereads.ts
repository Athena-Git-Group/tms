/** 住客畫面讀的兩份資料。 */
export type GuestData = 'home' | 'requests';

/** 串流上每一種訊號要重讀哪幾份資料；`task` 不帶卡片，只能整份重讀清單。不在表上的訊號不重讀。 */
const REREADS_ON: Readonly<Record<string, readonly GuestData[]>> = {
  task: ['requests'],
  resync: ['home', 'requests'],
};

export function rereadsOn(event: string): readonly GuestData[] {
  return Object.hasOwn(REREADS_ON, event) ? REREADS_ON[event]! : [];
}
