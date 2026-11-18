/**
 * 單元測試跑在沒有瀏覽器的環境，沒有 `window.sessionStorage` 與 `window.localStorage`。
 * 這裡用放在記憶體的簡易版代替，而且要在載入各個 store 之前裝好，因為 store 建立時就會去讀儲存空間。
 */
export function memoryStorage(): Storage {
  const items = new Map<string, string>();
  return {
    get length() {
      return items.size;
    },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => [...items.keys()][index] ?? null,
    removeItem: (key) => void items.delete(key),
    setItem: (key, value) => void items.set(key, value),
  };
}
