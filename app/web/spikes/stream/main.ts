// T006 spike：用 fetch 讀 GET /staff/stream 的最小頁面。不被 src/ 引用、不進打包、不是產品的串流客戶端。
// 由測試程式呼叫 window.__stream.start({ token }) 開始；所有觀察到的事都記在 window.__stream.log。

type Entry = { at: number; kind: string; detail?: string };
type Options = { token: string; path?: string; retryMs?: number; staleMs?: number };

const log: Entry[] = [];
let state = '尚未連線';
let running = false;
let controller: AbortController | null = null;
let lastDataAt = 0;
let staleReported = false;

function record(kind: string, detail?: string) {
  log.push({ at: Date.now(), kind, detail });
  const pre = document.querySelector('[data-testid="log"]');
  if (pre) pre.textContent += `${new Date().toISOString()} ${kind} ${detail ?? ''}\n`;
}

function setState(next: string) {
  state = next;
  const p = document.querySelector('[data-testid="state"]');
  if (p) p.textContent = next;
}

/** 一則事件以空行結束；每行是「欄位:值」，值前面的一個空白可有可無。 */
function parseBlock(block: string) {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    if (field === 'data') data.push(value);
  }
  return { event, data: data.join('\n') };
}

async function connectOnce(options: Options) {
  controller = new AbortController();
  setState('連線中');
  record('connecting');
  try {
    const response = await fetch(options.path ?? '/staff/stream', {
      headers: { Authorization: `Bearer ${options.token}`, Accept: 'text/event-stream' },
      cache: 'no-store',
      signal: controller.signal,
    });
    record('response', `${response.status} ${response.headers.get('content-type') ?? ''}`);
    if (!response.ok || !response.body) {
      setState('連線失敗');
      return;
    }
    setState('已連線');
    lastDataAt = Date.now();
    staleReported = false;
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      lastDataAt = Date.now();
      staleReported = false;
      buffer = (buffer + value).replace(/\r\n/g, '\n');
      let end = buffer.indexOf('\n\n');
      while (end >= 0) {
        const { event, data } = parseBlock(buffer.slice(0, end));
        buffer = buffer.slice(end + 2);
        record(event, data);
        end = buffer.indexOf('\n\n');
      }
    }
    record('ended', '後端或代理正常結束了回應');
  } catch (error) {
    record('error', String(error));
  }
  setState('已斷線');
}

async function start(options: Options) {
  if (running) return;
  running = true;
  while (running) {
    await connectOnce(options);
    if (!running) break;
    await new Promise((resolve) => setTimeout(resolve, options.retryMs ?? 1000));
  }
}

function stop() {
  running = false;
  controller?.abort();
}

// 連線還開著但太久沒有任何資料（後端卡住、網路半斷）：只記一筆，不主動斷線。
function watch(staleMs: number) {
  setInterval(() => {
    if (state === '已連線' && !staleReported && Date.now() - lastDataAt > staleMs) {
      staleReported = true;
      setState('太久沒有資料');
      record('stale', `已 ${Date.now() - lastDataAt} 毫秒沒有收到任何資料`);
    }
  }, 500);
}

Object.assign(window, {
  __stream: {
    log,
    state: () => state,
    start(options: Options) {
      watch(options.staleMs ?? 35000);
      void start(options);
    },
    stop,
    abort: () => controller?.abort(),
  },
});
