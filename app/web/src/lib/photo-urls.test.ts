import { describe, expect, it } from 'vitest';
import { diffPhotoUrls, photoUrlTracker } from './photo-urls';

function fakeUrls() {
  let next = 0;
  const created: string[] = [];
  const revoked: string[] = [];
  return {
    created,
    revoked,
    create: () => {
      const url = `blob:test/${next++}`;
      created.push(url);
      return url;
    },
    revoke: (url: string) => {
      revoked.push(url);
    },
  };
}

const blob = () => new Blob([new Uint8Array([0xff, 0xd8])], { type: 'image/jpeg' });

describe('diffPhotoUrls', () => {
  it('第一次全部要新建，沒有要釋放的', () => {
    expect(diffPhotoUrls([], ['a', 'b'])).toEqual({ create: ['a', 'b'], release: [] });
  });

  it('同一個 id 不重抓，只新建多出來的、釋放拿掉的', () => {
    expect(diffPhotoUrls(['a', 'b'], ['b', 'c'])).toEqual({ create: ['c'], release: ['a'] });
  });

  it('換單時舊的全部釋放', () => {
    expect(diffPhotoUrls(['a', 'b'], ['x'])).toEqual({ create: ['x'], release: ['a', 'b'] });
  });
});

describe('photoUrlTracker', () => {
  it('同一份清單再同步一次不會再抓', () => {
    const urls = fakeUrls();
    const tracker = photoUrlTracker(urls.create, urls.revoke);

    expect(tracker.sync(['a', 'b']).map((job) => job.id)).toEqual(['a', 'b']);
    expect(tracker.sync(['a', 'b'])).toEqual([]);
  });

  it('清單拿掉的照片中止請求、釋放已建的網址', () => {
    const urls = fakeUrls();
    const tracker = photoUrlTracker(urls.create, urls.revoke);
    const [jobA, jobB] = tracker.sync(['a', 'b']);
    const urlA = tracker.resolve('a', blob());

    tracker.sync(['c']);

    expect(jobA!.signal.aborted).toBe(true);
    expect(jobB!.signal.aborted).toBe(true);
    expect(urls.revoked).toEqual([urlA]);
  });

  it('已經不要的照片才回來時不建網址', () => {
    const urls = fakeUrls();
    const tracker = photoUrlTracker(urls.create, urls.revoke);
    tracker.sync(['a']);
    tracker.sync([]);

    expect(tracker.resolve('a', blob())).toBeUndefined();
    expect(urls.created).toEqual([]);
  });

  it('卸載時每個建過的網址都被釋放一次', () => {
    const urls = fakeUrls();
    const tracker = photoUrlTracker(urls.create, urls.revoke);
    tracker.sync(['a', 'b', 'c']);
    tracker.resolve('a', blob());
    tracker.resolve('b', blob());

    tracker.release();
    tracker.release();

    expect([...urls.revoked].sort()).toEqual([...urls.created].sort());
    expect(urls.revoked).toHaveLength(2);
  });
});
