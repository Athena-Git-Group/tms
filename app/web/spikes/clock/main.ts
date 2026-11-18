/**
 * T005 spike：確認 Playwright 撥瀏覽器時鐘之後，畫面上的「等 N 分」跟著變。不是產品程式，`src/` 不得引用。
 * 開發伺服器起來後開 `/spikes/clock/index.html`。做法和產品預計的一樣：記下起算時刻，
 * 每秒用 `Date.now()` 重算一次。`data-testid`：`wait`（「等 N 分」）、`now`（頁面看到的現在時刻）、
 * `ticks`（計時器跑了幾次）。
 */
const startedAt = Date.now();
let ticks = 0;

function render(): void {
  const minutes = Math.floor((Date.now() - startedAt) / 60_000);
  document.querySelector('[data-testid="wait"]')!.textContent = `等 ${minutes} 分`;
  document.querySelector('[data-testid="now"]')!.textContent = new Date().toISOString();
  document.querySelector('[data-testid="ticks"]')!.textContent = String(ticks);
}

render();
setInterval(() => {
  ticks += 1;
  render();
}, 1000);
