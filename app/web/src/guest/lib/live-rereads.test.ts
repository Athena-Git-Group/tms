import { describe, expect, it } from 'vitest';
import { rereadsOn } from './live-rereads';

describe('rereadsOn', () => {
  it('re-reads only the request list when one task changed', () => {
    expect(rereadsOn('task')).toEqual(['requests']);
  });

  it('re-reads home data and the request list when the backend asks for a resync', () => {
    expect(rereadsOn('resync')).toEqual(['home', 'requests']);
  });

  it.each(['init', 'heartbeat', 'degraded', 'message', 'toString'])('ignores %s', (event) => {
    expect(rereadsOn(event)).toEqual([]);
  });
});
