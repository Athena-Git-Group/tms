import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FIRST_RETRY_MS, LATER_RETRY_MS, SILENCE_LIMIT_MS, type LiveLink, type LiveState } from './live-connection';
import { openStaffStream, takeStreamEvents, type StreamEvent } from './stream-client';

function streamResponse(text: string): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text));
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('openStaffStream', () => {
  const fetchMock = vi.fn<typeof fetch>();
  let connections: LiveLink[];
  let states: LiveState[];
  let events: StreamEvent[];

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    connections = [];
    states = [];
    events = [];
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  const open = () =>
    openStaffStream({
      onEvent: (event) => events.push(event),
      onState: (state) => {
        states.push(state);
        if (connections.at(-1) !== state.link) connections.push(state.link);
      },
    });

  it('一直連不上時，第一次 1 秒後重連，之後每 5 秒', async () => {
    fetchMock.mockRejectedValue(new TypeError('failed to fetch'));
    const close = open();
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(connections).toEqual(['connecting', 'disconnected']);

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS - 1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(connections).toEqual(['connecting', 'disconnected', 'reconnecting', 'disconnected']);

    await vi.advanceTimersByTimeAsync(LATER_RETRY_MS - 1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    close();
  });

  it('回應不是成功（例如 401、502）也當斷線', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    const close = open();
    await vi.advanceTimersByTimeAsync(0);
    expect(connections).toEqual(['connecting', 'disconnected']);
    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    close();
  });

  it('連上後交出訊號；收過資料後斷線，下一次一樣 1 秒後重連', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('failed to fetch'));
    fetchMock.mockResolvedValueOnce(streamResponse('event:task\ndata:{"seq":1}\n\n'));
    fetchMock.mockRejectedValue(new TypeError('failed to fetch'));
    const close = open();
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(events).toEqual([{ event: 'task', data: '{"seq":1}' }]);
    expect(connections).toEqual(['connecting', 'disconnected', 'reconnecting', 'connected', 'disconnected']);

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    close();
  });

  it('帶登入憑證與串流的 Accept 去連', async () => {
    fetchMock.mockRejectedValue(new TypeError('failed to fetch'));
    const close = open();
    await vi.advanceTimersByTimeAsync(0);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(String(url)).toMatch(/\/staff\/stream$/);
    expect((init?.headers as Record<string, string>).Accept).toBe('text/event-stream');
    close();
  });

  it('連上之後超過 35 秒沒有資料：關掉連線、變成不可信，1 秒後重連', async () => {
    let aborted = false;
    fetchMock.mockImplementationOnce((_url, init) => {
      init?.signal?.addEventListener('abort', () => {
        aborted = true;
      });
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('event:init\ndata:{}\n\n'));
          init?.signal?.addEventListener('abort', () => controller.error(new DOMException('aborted', 'AbortError')));
        },
      });
      return Promise.resolve(new Response(body, { status: 200 }));
    });
    fetchMock.mockRejectedValue(new TypeError('failed to fetch'));
    const close = open();
    await vi.advanceTimersByTimeAsync(0);
    expect(connections).toEqual(['connecting', 'connected']);
    expect(states.at(-1)?.trusted).toBe(true);

    await vi.advanceTimersByTimeAsync(SILENCE_LIMIT_MS - 1);
    expect(aborted).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(aborted).toBe(true);
    expect(connections).toEqual(['connecting', 'connected', 'disconnected']);
    expect(states.at(-1)?.trusted).toBe(false);

    await vi.advanceTimersByTimeAsync(FIRST_RETRY_MS);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    close();
  });

  it('關掉之後不再重連，也不再報連線現況', async () => {
    fetchMock.mockRejectedValue(new TypeError('failed to fetch'));
    const close = open();
    await vi.advanceTimersByTimeAsync(0);
    close();
    const reported = connections.length;
    await vi.advanceTimersByTimeAsync(LATER_RETRY_MS * 3);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(connections).toHaveLength(reported);
  });
});

describe('takeStreamEvents', () => {
  it('切出後端寫法（冒號後沒有空白）的訊號', () => {
    const { events, rest } = takeStreamEvents('event:init\ndata:{"stream_pos":0}\n\nevent:resync\ndata:{"reason":"connected"}\n\n');
    expect(events).toEqual([
      { event: 'init', data: '{"stream_pos":0}' },
      { event: 'resync', data: '{"reason":"connected"}' },
    ]);
    expect(rest).toBe('');
  });

  it('冒號後有一個空白時去掉它', () => {
    expect(takeStreamEvents('event: task\ndata: {}\n\n').events).toEqual([{ event: 'task', data: '{}' }]);
  });

  it('還沒收到空行的那一段留到下一塊資料', () => {
    const first = takeStreamEvents('event:task\ndata:{"seq"');
    expect(first.events).toEqual([]);
    const second = takeStreamEvents(first.rest + ':1}\n\nevent:heart');
    expect(second.events).toEqual([{ event: 'task', data: '{"seq":1}' }]);
    expect(second.rest).toBe('event:heart');
  });

  it('認得 CRLF 換行', () => {
    expect(takeStreamEvents('event:heartbeat\r\ndata:{}\r\n\r\n').events).toEqual([{ event: 'heartbeat', data: '{}' }]);
  });

  it('\\r\\n 被切在兩塊資料之間時不會多出一個空行', () => {
    const first = takeStreamEvents('event:task\r');
    expect(first.events).toEqual([]);
    const second = takeStreamEvents(first.rest + '\ndata:{}\r\n\r\n');
    expect(second.events).toEqual([{ event: 'task', data: '{}' }]);
  });

  it('略過註解行與沒有欄位的空段', () => {
    expect(takeStreamEvents(':keep-alive\n\nevent:task\n:note\ndata:{}\n\n').events).toEqual([
      { event: 'task', data: '{}' },
    ]);
  });

  it('沒有 event 欄位時名稱是 message，多行 data 以換行接起來', () => {
    expect(takeStreamEvents('data:a\ndata:b\n\n').events).toEqual([{ event: 'message', data: 'a\nb' }]);
  });
});
