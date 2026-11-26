export function diffPhotoUrls(previous: Iterable<string>, nextIds: readonly string[]): { create: string[]; release: string[] } {
  const before = new Set(previous);
  const after = new Set(nextIds);
  return {
    create: nextIds.filter((id) => !before.has(id)),
    release: [...before].filter((id) => !after.has(id)),
  };
}

export interface PhotoFetchJob {
  id: string;
  signal: AbortSignal;
}

export interface PhotoUrlTracker {
  sync(ids: readonly string[]): PhotoFetchJob[];
  resolve(id: string, blob: Blob): string | undefined;
  release(): void;
}

interface Entry {
  controller: AbortController;
  url?: string;
}

export function photoUrlTracker(create: (blob: Blob) => string, revoke: (url: string) => void): PhotoUrlTracker {
  const entries = new Map<string, Entry>();

  const drop = (id: string) => {
    const entry = entries.get(id);
    if (!entry) return;
    entries.delete(id);
    entry.controller.abort();
    if (entry.url) revoke(entry.url);
  };

  return {
    sync(ids) {
      const { create: added, release: removed } = diffPhotoUrls(entries.keys(), ids);
      removed.forEach(drop);
      return added.map((id) => {
        const controller = new AbortController();
        entries.set(id, { controller });
        return { id, signal: controller.signal };
      });
    },
    resolve(id, blob) {
      const entry = entries.get(id);
      if (!entry || entry.controller.signal.aborted) return undefined;
      entry.url ??= create(blob);
      return entry.url;
    },
    release() {
      [...entries.keys()].forEach(drop);
    },
  };
}
