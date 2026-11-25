/* 代開單面板的靜態雛形：房號、建議房號、住客卡、空房提示、12 個常用品項、其他需求、類似單提醒、摘要、送出、送出失敗與成功後回看板。看板資料與送出後的換頁由 desk.js 的 DeskDemo 提供。 */
(function () {
  'use strict';
  const D = window.DeskDemo;
  if (!D) return;
  const esc = D.esc;
  const hk = 'housekeeping', eng = 'engineering';

  /* 12 個品項的圖示，取自原型的 SF Symbols 字形。 */
  const GLYPH = {"towel":"M7.08 4.49Q7.15 3.5 8.17 3.5H15.86Q16.88 3.5 16.96 4.49ZM5.6 6.57Q5.74 5.51 6.8 5.51H17.24Q18.3 5.51 18.44 6.57ZM6.27 20.5Q3.91 20.5 3.91 18.17V10.13Q3.91 7.8 6.27 7.8H17.73Q20.09 7.8 20.09 10.13V18.17Q20.09 20.5 17.73 20.5ZM6.3 19.3H17.73Q18.9 19.3 18.9 18.1V10.2Q18.9 9 17.73 9H6.3Q5.1 9 5.1 10.2V18.1Q5.1 19.3 6.3 19.3Z","bath":"M8.4 4.42Q8.46 3.5 9.45 3.5H14.38Q15.4 3.5 15.43 4.42ZM7.1 6.46Q7.27 5.4 8.23 5.4H15.54Q16.52 5.4 16.69 6.46ZM7.82 20.5Q5.54 20.5 5.54 18.26V9.86Q5.54 7.61 7.82 7.61H16.15Q18.46 7.61 18.46 9.86V18.26Q18.46 20.5 16.39 20.5ZM7.85 19.34H16.35Q16.76 19.34 17.02 19.04Q17.27 18.73 17.27 18.19V9.93Q17.27 8.77 16.15 8.77H7.85Q6.7 8.77 6.7 9.93V18.19Q6.7 19.34 7.85 19.34Z","water":"M7.57 14.67Q7.57 16.54 8.86 17.83Q10.15 19.12 11.98 19.12Q13.81 19.12 15.12 17.81Q16.43 16.5 16.43 14.67Q16.43 11.68 11.98 5.41Q7.57 11.65 7.57 14.67ZM11.98 20.5Q9.59 20.5 7.89 18.8Q6.19 17.1 6.19 14.67Q6.19 11.12 11.46 3.84Q11.53 3.69 11.61 3.65Q12.13 3.28 12.54 3.84Q17.81 11.12 17.81 14.67Q17.81 17.1 16.11 18.8Q14.41 20.5 11.98 20.5ZM11.76 17.36Q12.92 17.4 13.81 16.5Q14.75 15.57 14.71 14.45Q14.71 13.96 15.12 13.96Q15.57 13.96 15.57 14.37V14.45Q15.38 16.2 14.45 17.14Q13.51 18.07 11.76 18.26Q11.61 18.3 11.46 18.16Q11.31 18.03 11.27 17.85V17.81Q11.27 17.36 11.76 17.36Z","slipper":"M5.23 16.87Q4.1 16.25 4.1 14.91V8.73Q4.1 7.57 5.08 7.02L10.57 3.9Q11.98 3.1 13.4 3.9L18.92 7.02Q19.9 7.57 19.9 8.73V14.91Q19.9 16.25 18.77 16.87L12.67 20.32Q11.98 20.68 11.33 20.32ZM15.65 9.24 17.94 7.97 12.85 5.06Q11.98 4.59 11.15 5.06L9.69 5.86ZM11.98 11.31 14.34 10 8.39 6.62 6.06 7.97ZM5.95 15.78 11.33 18.83V12.47L5.37 9.06V14.87Q5.37 15.45 5.95 15.78ZM18.05 15.78Q18.63 15.45 18.63 14.87V9.06L12.64 12.47V18.83Z","tooth":"M5.23 16.87Q4.1 16.25 4.1 14.91V8.73Q4.1 7.57 5.08 7.02L10.57 3.9Q11.98 3.1 13.4 3.9L18.92 7.02Q19.9 7.57 19.9 8.73V14.91Q19.9 16.25 18.77 16.87L12.67 20.32Q11.98 20.68 11.33 20.32ZM15.65 9.24 17.94 7.97 12.85 5.06Q11.98 4.59 11.15 5.06L9.69 5.86ZM11.98 11.31 14.34 10 8.39 6.62 6.06 7.97ZM5.95 15.78 11.33 18.83V12.47L5.37 9.06V14.87Q5.37 15.45 5.95 15.78ZM18.05 15.78Q18.63 15.45 18.63 14.87V9.06L12.64 12.47V18.83Z","crib":"M15.33 6.66Q15.71 6.66 15.71 7.04Q15.71 7.42 15.33 7.42H12.83Q12.42 7.42 12.42 7.04Q12.42 6.9 12.62 6.59L14.39 4.3V4.26H12.8Q12.38 4.26 12.38 3.88Q12.38 3.5 12.8 3.5H15.16Q15.61 3.5 15.61 3.88Q15.61 3.95 15.37 4.33L13.63 6.62V6.66ZM18.83 9.5Q19.18 9.5 19.18 9.85Q19.18 10.23 18.83 10.23H16.89Q16.48 10.23 16.48 9.88Q16.48 9.71 16.68 9.43L17.9 7.84V7.8H16.82Q16.48 7.8 16.48 7.42Q16.48 7.07 16.82 7.07H18.66Q19.04 7.07 19.04 7.42Q19.04 7.56 18.87 7.84L17.62 9.47V9.5ZM11.31 20.5Q8.46 20.5 6.64 18.68Q4.82 16.86 4.82 14.12Q4.82 12.03 5.88 10.37Q6.93 8.7 8.67 8.01Q9.19 7.8 9.4 8.08Q9.61 8.29 9.4 8.81Q9.02 9.57 9.02 11.06Q9.02 13.53 10.49 14.97Q11.97 16.41 14.46 16.41Q15.64 16.41 16.58 16.06Q17 15.89 17.2 16.09Q17.45 16.34 17.24 16.82Q16.48 18.56 14.91 19.53Q13.35 20.5 11.31 20.5ZM11.34 19.39Q14.26 19.39 15.78 17.31Q15.26 17.48 14.19 17.48Q11.38 17.48 9.64 15.8Q7.91 14.12 7.91 11.38Q7.91 10.3 8.22 9.43Q5.96 11.13 5.96 14.01Q5.96 16.34 7.47 17.86Q8.98 19.39 11.34 19.39ZM15.54 12.03Q15.89 12.03 15.89 12.38Q15.89 12.73 15.54 12.73H13.8Q13.46 12.73 13.46 12.38Q13.46 12.17 13.63 11.97L14.67 10.58V10.54H13.77Q13.42 10.54 13.42 10.2Q13.42 9.85 13.77 9.85H15.37Q15.75 9.85 15.75 10.2Q15.75 10.33 15.57 10.61L14.53 12V12.03Z","bed":"M4.06 17.64Q3.5 17.64 3.5 17.08V12.5Q3.5 10.71 5.26 10.54V8.21Q5.26 6.36 7.12 6.36H16.88Q18.74 6.36 18.74 8.21V10.54Q20.5 10.71 20.5 12.5V17.08Q20.5 17.64 19.94 17.64H19.54Q18.97 17.64 18.97 17.08V16.08Q18.91 16.12 18.51 16.12H5.49Q5.09 16.12 5.03 16.08V17.08Q5.03 17.64 4.46 17.64ZM7.02 9.28V10.54H11.34V9.28Q11.34 8.31 10.34 8.31H7.98Q7.02 8.31 7.02 9.28ZM12.66 9.28V10.54H16.98V9.28Q16.98 8.31 16.02 8.31H13.66Q12.66 8.31 12.66 9.28ZM4.89 15.09H19.11Q19.34 15.09 19.34 14.82V12.4Q19.34 12.03 19.09 11.8Q18.84 11.57 18.47 11.57H5.53Q4.66 11.57 4.66 12.4V14.82Q4.66 15.09 4.89 15.09Z","ice":"M7.05 17.21V15.52L5.5 16.46Q4.98 16.79 4.65 16.23Q4.32 15.76 4.89 15.43L6.48 14.49L5.03 13.69Q4.51 13.41 4.79 12.89Q5.03 12.38 5.59 12.7L7.56 13.88L8.45 13.36L11.18 12L8.45 10.64L7.56 10.12L5.59 11.25Q4.98 11.58 4.74 11.11Q4.46 10.64 5.03 10.31L6.48 9.46L4.89 8.57Q4.32 8.24 4.65 7.73Q4.98 7.26 5.5 7.54L7.05 8.43V6.79Q7.05 6.18 7.61 6.18Q8.17 6.18 8.17 6.79L8.13 9.09L9.06 9.6L11.6 11.3L11.41 8.24V7.21L9.44 6.08Q8.92 5.8 9.16 5.28Q9.44 4.81 9.96 5.1L11.41 5.94V4.11Q11.41 3.5 12.02 3.5Q12.59 3.5 12.59 4.11V5.94L14.04 5.1Q14.56 4.81 14.84 5.28Q15.08 5.8 14.61 6.08L12.59 7.21V8.24L12.45 11.25L14.94 9.6L15.87 9.09V6.79Q15.87 6.18 16.39 6.18Q16.95 6.18 16.95 6.79V8.48L18.55 7.54Q19.02 7.26 19.35 7.73Q19.68 8.24 19.11 8.57L17.56 9.46L18.97 10.31Q19.54 10.64 19.26 11.11Q19.02 11.58 18.46 11.25L16.48 10.12L15.55 10.64L12.82 12L15.55 13.36L16.48 13.88L18.46 12.7Q19.02 12.38 19.26 12.89Q19.58 13.36 18.97 13.69L17.56 14.49L19.11 15.43Q19.68 15.76 19.35 16.23Q19.07 16.74 18.55 16.46L16.95 15.52V17.21Q16.95 17.82 16.39 17.82Q15.87 17.82 15.87 17.21V14.91L14.94 14.4L12.45 12.7L12.59 15.76V16.79L14.61 17.92Q15.08 18.2 14.84 18.72Q14.56 19.14 14.04 18.86L12.59 18.06V19.89Q12.59 20.5 12.02 20.5Q11.41 20.5 11.41 19.89V18.06L9.96 18.86Q9.39 19.19 9.16 18.72Q8.92 18.2 9.44 17.92L11.41 16.79V15.76L11.6 12.7L9.06 14.4L8.13 14.91L8.17 17.21Q8.17 17.82 7.61 17.82Q7.05 17.82 7.05 17.21Z","ac":"M4.22 9.64Q3.68 9.8 3.52 9.29Q3.41 8.75 3.91 8.6Q5.88 7.98 7.85 7.98Q8.67 7.98 9.96 8.09Q11.26 8.21 11.91 8.21Q13.27 8.21 13.27 6.97Q13.27 6.39 12.92 6.06Q12.57 5.73 12.03 5.73Q11.22 5.73 10.87 6.47Q10.64 7.01 10.14 6.78Q9.67 6.55 9.9 6.04Q10.17 5.46 10.75 5.06Q11.33 4.65 12.03 4.65Q13 4.65 13.67 5.31Q14.35 5.97 14.35 6.97Q14.35 7.98 13.69 8.64Q13.04 9.29 11.91 9.29Q11.22 9.29 9.92 9.18Q8.63 9.06 7.85 9.06Q6.04 9.06 4.22 9.64ZM17.21 13.51Q15.9 13.51 13.13 12.79Q10.37 12.08 8.63 12.08Q6.19 12.08 4.22 12.81Q3.68 13.01 3.52 12.46Q3.41 11.92 3.91 11.77Q6.15 10.99 8.63 10.99Q10.41 10.99 13.23 11.71Q16.05 12.43 17.21 12.43Q18.22 12.43 18.82 11.9Q19.42 11.38 19.42 10.57Q19.42 9.76 18.88 9.23Q18.33 8.71 17.56 8.71Q16.94 8.71 16.46 9.12Q15.98 9.52 15.86 10.18Q15.74 10.8 15.2 10.72Q14.7 10.65 14.78 10.07Q14.93 8.98 15.68 8.31Q16.44 7.63 17.56 7.63Q18.8 7.63 19.65 8.46Q20.5 9.29 20.5 10.57Q20.5 11.85 19.57 12.68Q18.64 13.51 17.21 13.51ZM11.95 19.35Q11.02 19.35 10.41 18.75Q9.79 18.15 9.67 17.26Q9.59 16.64 10.14 16.6Q10.68 16.52 10.75 17.14Q10.95 18.27 11.95 18.27Q12.46 18.27 12.78 17.96Q13.11 17.65 13.11 17.07Q13.11 16.14 11.91 15.62Q10.72 15.09 8.51 15.09Q6.31 15.09 4.22 15.83Q3.68 16.02 3.52 15.48Q3.41 14.94 3.91 14.78Q6.04 14.01 8.51 14.01Q14.2 14.01 14.2 17.07Q14.2 18.07 13.56 18.71Q12.92 19.35 11.95 19.35Z","hot":"M5.43 15.12Q5.43 13.56 6.1 11.87Q6.77 10.18 6.77 9.53Q6.77 9.35 6.73 8.99Q6.7 8.62 6.7 8.55Q6.7 8.26 6.88 8.06Q7.06 7.86 7.35 7.86Q8.51 7.86 9.24 9.24Q9.24 9.17 9.26 9.04Q9.28 8.91 9.28 8.84Q9.24 7.17 7.42 4.99Q7.13 4.7 7.13 4.34Q7.13 3.5 8.48 3.5Q10.11 3.5 11.8 4.08Q13.49 4.66 15.03 5.81Q16.58 6.95 17.58 8.95Q18.57 10.95 18.57 13.45Q18.57 16.65 16.65 18.57Q14.72 20.5 11.6 20.5Q8.8 20.5 7.11 19.03Q5.43 17.56 5.43 15.12ZM6.73 15.12Q6.73 16.98 8.1 18.1Q9.46 19.23 11.71 19.23Q14.18 19.23 15.72 17.63Q17.27 16.03 17.27 13.42Q17.27 9.86 14.98 7.44Q12.69 5.03 8.77 4.7Q10.4 6.33 10.4 8.84Q10.4 9.86 10.07 10.87Q9.75 11.89 9.24 11.89Q8.99 11.89 8.99 11.67Q8.84 10.37 8.11 9.46Q8.08 10.55 7.4 12.22Q6.73 13.89 6.73 15.12ZM11.82 18.32Q10.69 18.32 10 17.7Q9.31 17.09 9.31 16.07Q9.31 15.27 9.88 14.42Q10.44 13.56 10.47 13.24Q10.47 13.09 10.58 13.2Q11.09 13.67 11.38 14.4Q11.46 14.51 11.49 14.4Q12.07 13.38 11.89 11.27Q11.89 11.13 12 11.2Q13.27 11.78 13.96 12.94Q14.65 14.11 14.65 15.34Q14.65 16.61 13.91 17.47Q13.16 18.32 11.82 18.32Z","light":"M9.34 16.76Q8.93 16.76 8.93 16.35V15.15Q8.93 14.43 8.3 13.58Q7.66 12.72 7.01 11.59Q6.36 10.46 6.36 9.09Q6.36 6.58 7.92 5.04Q9.48 3.5 12.02 3.5Q14.55 3.5 16.1 5.04Q17.64 6.58 17.64 9.09Q17.64 10.46 17 11.59Q16.37 12.72 15.72 13.58Q15.07 14.43 15.07 15.15V16.35Q15.07 16.76 14.66 16.76ZM10.03 15.19V15.63H13.97V15.19Q13.97 14.47 14.36 13.71Q14.76 12.96 15.26 12.38Q15.75 11.79 16.15 10.9Q16.54 10.01 16.54 9.09Q16.54 7.06 15.29 5.83Q14.04 4.6 12.02 4.6Q9.99 4.6 8.73 5.85Q7.46 7.1 7.46 9.09Q7.46 10.01 7.85 10.9Q8.25 11.79 8.74 12.38Q9.24 12.96 9.64 13.71Q10.03 14.47 10.03 15.19ZM9.62 18.72Q9.34 18.72 9.14 18.51Q8.93 18.31 8.93 18.03Q8.93 17.76 9.14 17.55Q9.34 17.35 9.62 17.35H14.38Q14.66 17.35 14.86 17.55Q15.07 17.76 15.07 18.03Q15.07 18.31 14.86 18.51Q14.66 18.72 14.38 18.72ZM12.02 20.5Q11.23 20.5 10.73 20.19Q10.23 19.88 10.2 19.33H13.8Q13.7 20.5 12.02 20.5Z","tv":"M5.16 16.15Q3.5 16.15 3.5 14.49V7.12Q3.5 5.46 5.16 5.46H18.84Q20.5 5.46 20.5 7.12V14.49Q20.5 16.15 18.84 16.15ZM5.16 15.12H18.84Q19.47 15.12 19.47 14.49V7.12Q19.47 6.49 18.84 6.49H5.16Q4.53 6.49 4.53 7.12V14.49Q4.53 15.12 5.16 15.12ZM16.08 18.54H7.92Q7.68 18.54 7.5 18.38Q7.32 18.21 7.32 17.94Q7.32 17.68 7.5 17.51Q7.68 17.35 7.92 17.35H16.08Q16.32 17.35 16.5 17.51Q16.68 17.68 16.68 17.94Q16.68 18.21 16.5 18.38Q16.32 18.54 16.08 18.54Z"};
  const ITEMS = [
    { key: '毛巾', icon: 'towel', dept: hk, max: 9 }, { key: '浴巾', icon: 'bath', dept: hk, max: 9 },
    { key: '礦泉水', icon: 'water', dept: hk, max: 9 }, { key: '拖鞋', icon: 'slipper', dept: hk, max: 9 },
    { key: '牙刷組', icon: 'tooth', dept: hk, max: 9 }, { key: '嬰兒床', icon: 'crib', dept: hk, max: 2 },
    { key: '加床', icon: 'bed', dept: hk, max: 2 }, { key: '冰塊', icon: 'ice', dept: hk, max: 2, unit: '桶' },
    { key: '冷氣', icon: 'ac', dept: eng, max: 1 }, { key: '熱水', icon: 'hot', dept: eng, max: 1 },
    { key: '燈具', icon: 'light', dept: eng, max: 1 }, { key: '電視', icon: 'tv', dept: eng, max: 1 }
  ];
  const DEPT_LABEL = { housekeeping: '房務', engineering: '工務' };
  const OPEN_STATES = ['triage', 'pool', 'accepted', 'doing'];
  const X_PATH = 'M1409.2 37.2 36.2 1410.5C-11.8 1458.2 -12.8 1544.5 37.2 1594.2C87.6 1643.5 174.6 1642.5 221.9 1595.2L1594.9 222.2C1643.9 173.2 1644.2 88.5 1593.6 38.5C1542.9 -12.1 1459.2 -13.1 1409.2 37.2ZM1594.9 1409.9 221.9 36.9C174.2 -11.1 87.2 -13.1 37.2 38.2C-12.4 88.9 -11.4 174.2 36.2 221.9L1409.2 1594.9C1458.2 1643.9 1543.6 1644.2 1593.6 1593.9C1643.6 1543.2 1643.9 1459.2 1594.9 1409.9Z';
  const WARN_PATH = 'M4.78 20.2Q3.79 20.2 3.17 19.56Q2.55 18.91 2.55 17.97Q2.55 17.33 2.85 16.86L10.09 3.91Q10.74 2.8 12.02 2.8Q13.31 2.8 13.95 3.91L21.15 16.86Q21.45 17.33 21.45 17.97Q21.45 18.91 20.83 19.56Q20.21 20.2 19.22 20.2ZM12.02 13.9Q12.75 13.9 12.75 13.13L12.92 8.63Q12.92 8.24 12.64 7.99Q12.36 7.73 11.98 7.73Q11.12 7.73 11.12 8.59L11.25 13.13Q11.25 13.9 12.02 13.9ZM11.31 17.01Q11.64 17.29 12.02 17.29Q12.41 17.29 12.73 17.01Q13.05 16.73 13.05 16.3Q13.05 15.87 12.75 15.59Q12.45 15.31 12.02 15.31Q11.59 15.31 11.29 15.59Q10.99 15.87 10.99 16.3Q10.99 16.73 11.31 17.01Z';

  /* 房間清單：網址 rooms=loading 讓第一次讀取停 2.5 秒，rooms=error 讓它讀不到；讀到一次之後就當作快取，之後打開不再等。 */
  const roomList = { mode: D.Q.get('rooms') === 'error' ? 'error' : D.Q.get('rooms') === 'loading' ? 'loading' : 'ok', timer: null };
  /* 回應遺失（create=lost）時，記下哪一把冪等鍵其實已經開好了哪幾張；同一把鍵再送就回放這批，不再開一次。 */
  const committed = {};
  let st = null, opener = null;

  const digits = (v) => v.replace(/[^0-9]/g, '').slice(0, 4);
  const cut60 = (v) => Array.from(v).slice(0, 60).join('');
  const picks = () => ITEMS.filter((it) => (st.qty[it.key] || 0) > 0);
  const otherText = () => st.text.trim();
  const count = () => picks().length + (otherText() ? 1 : 0);
  const occupied = () => Object.keys(D.GUESTS).filter((r) => D.ROOMS.indexOf(r) >= 0).sort((a, b) => +a - +b);

  /* 房號有效＝房間清單裡有；清單還在讀時判斷不了（不能送）；讀不到清單時只要有數字就先當有效，交給送出時的後端檢查。 */
  function roomInfo() {
    const r = st.room;
    if (!r || roomList.mode === 'loading') return null;
    if (roomList.mode === 'error') return { room: r, relaxed: true };
    if (D.ROOMS.indexOf(r) < 0) return null;
    return { room: r, guest: D.GUESTS[r] || null };
  }
  const canSend = () => !!roomInfo() && count() > 0 && !st.sending;

  function summary() {
    const n = count(); if (!n) return '';
    const hkL = picks().filter((it) => it.dept === hk).map((it) => it.key + ' ×' + st.qty[it.key]);
    const enL = picks().filter((it) => it.dept === eng).map((it) => it.key);
    if (otherText()) (st.textDept === hk ? hkL : enL).push('其他需求');
    const parts = [];
    if (hkL.length) parts.push(hkL.join('、') + ' 給房務');
    if (enL.length) parts.push(enL.join('、') + ' 給工務');
    return '會開 ' + n + ' 張單：' + parts.join('；') + '。直接進部門待認領，不經派單。';
  }

  function similar() {
    const info = roomInfo(); if (!info) return [];
    const keys = picks().map((it) => it.key); const text = otherText();
    return D.tasks()
      .filter((x) => x.room === info.room && OPEN_STATES.indexOf(x.state) >= 0 && !x.answered && ((x.item && keys.indexOf(x.item) >= 0) || (text && x.free)))
      .sort((p, q) => q.c - p.c)
      .map((x) => { const m = D.mins(x.c); return x.title + '：' + D.tagOf(x)[0] + (x.who ? ' · ' + x.who : '') + '，' + (m < 1 ? '剛剛' : m + ' 分鐘前') + '建立'; });
  }

  function shell() {
    const host = document.createElement('div');
    host.id = 'createHost';
    host.innerHTML =
      '<div class="cr-scrim"><button class="cr-scrim-btn" aria-label="關閉" tabindex="-1"></button>' +
        '<div class="cr-panel" role="dialog" aria-modal="true" aria-labelledby="crTitle" tabindex="-1">' +
          '<div class="cr-nav"><button class="cr-x" id="crCancel" aria-label="取消"><svg viewBox="-433 -433 2497 2497" aria-hidden="true"><path d="' + X_PATH + '"/></svg></button><h2 id="crTitle">代開單</h2><span></span></div>' +
          '<div class="cr-body" id="crBody">' +
            '<div class="cr-stack"><label class="cr-room"><span>房號</span><input id="crRoom" inputmode="numeric" maxlength="4" autocomplete="off" placeholder="輸入房號，例如 305"></label><div id="crRoomMore" class="cr-stack"></div></div>' +
            '<div class="cr-stack"><span class="cr-label">常用品項（點一下加一份，會依品項自動分到房務或工務）</span><div class="cr-grid" id="crGrid"></div></div>' +
            '<div class="cr-stack"><span class="cr-label">其他需求（選填）</span><label class="cr-text"><span class="sr">其他需求</span><textarea id="crText" rows="2" maxlength="60" placeholder="例如：住客明早 6 點要叫車、浴室門卡住"></textarea></label><div id="crGive"></div></div>' +
            '<div id="crDup" role="status"></div>' +
            '<span class="cr-summary" id="crSummary" aria-live="polite"></span>' +
          '</div>' +
          '<div class="cr-foot"><button class="cr-send" id="crSend" disabled>送出</button></div>' +
        '</div></div>';
    document.body.appendChild(host);
  }

  function drawRoom() {
    const more = document.getElementById('crRoomMore'); const info = roomInfo();
    let html = '';
    if (roomList.mode === 'loading') html = '<div class="cr-suggest" aria-busy="true"><span class="skel"></span><span class="skel"></span><span class="skel"></span></div>';
    else if (roomList.mode === 'error') html = '<div class="cr-roomfail"><span class="grow">讀不到房間清單，送出時再確認房號。</span><button class="btn-outline" id="crRetry">重試</button></div>';
    else if (!info) {
      const list = occupied().filter((r) => !st.room || r.indexOf(st.room) === 0).slice(0, 6);
      if (list.length) html = '<div class="cr-suggest">' + list.map((r) => '<button data-room="' + r + '"><b>' + r + '</b><span>' + esc(D.GUESTS[r].name) + '</span></button>').join('') + '</div>';
    } else if (info.guest) {
      const g = info.guest;
      html = '<div class="cr-guest"><span class="face" aria-hidden="true">' + esc(Array.from(g.name)[0]) + '</span><span class="cr-who"><b>' + esc(g.name) + '</b><span>' + info.room + ' · ' + esc(g.meta) + '</span></span>' + (g.vip ? '<span class="vip">VIP</span>' : '') + '</div>';
    } else html = '<span class="cr-vacant">' + info.room + ' 目前是空房，沒有住客。代開的單照常送出，例如請工務先修。</span>';
    if (more.dataset.html !== html) { more.innerHTML = html; more.dataset.html = html; }
  }

  function drawGrid() {
    document.getElementById('crGrid').innerHTML = ITEMS.map((it) => {
      const n = st.qty[it.key] || 0;
      let foot = '';
      if (n && it.max > 1) foot = '<div class="cr-step"><button data-f="minus:' + it.key + '" data-minus="' + it.key + '" aria-label="減一份' + it.key + '">−</button><b>' + n + '</b><button data-f="plus:' + it.key + '" data-add="' + it.key + '" aria-label="加一份' + it.key + '">+</button></div>';
      if (n && it.max === 1) foot = '<button class="cr-remove" data-f="remove:' + it.key + '" data-remove="' + it.key + '" aria-label="移除' + it.key + '">移除</button>';
      return '<div class="cr-tile' + (n ? ' on' : '') + '" data-dept="' + it.dept + '">' +
        '<button class="cr-add" data-f="add:' + it.key + '" data-add="' + it.key + '" aria-label="加一份' + it.key + (n ? '，目前 ' + n : '') + '">' +
          '<span class="cr-ico" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="' + GLYPH[it.icon] + '"/></svg>' + (n ? '<span class="cr-badge">' + n + '</span>' : '') + '</span>' +
          '<span class="cr-name">' + it.key + '</span><span class="cr-dept">' + DEPT_LABEL[it.dept] + '</span></button>' + foot + '</div>';
    }).join('');
  }

  function drawRest() {
    const give = document.getElementById('crGive');
    const giveHtml = otherText() ? '<div class="cr-give"><span>這段要給</span><div class="cr-seg" role="group" aria-label="其他需求的部門">' +
      [hk, eng].map((d) => '<button data-dept="' + d + '" data-f="dept:' + d + '" aria-pressed="' + (st.textDept === d) + '">' + DEPT_LABEL[d] + '</button>').join('') + '</div></div>' : '';
    if (give.dataset.html !== giveHtml) { give.innerHTML = giveHtml; give.dataset.html = giveHtml; }
    const lines = similar(); const dup = document.getElementById('crDup');
    const dupHtml = lines.length ? '<div class="cr-dup"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="' + WARN_PATH + '"/></svg><div><b>這間房已經有類似的單</b>' +
      lines.map((l) => '<span>' + esc(l) + '</span>').join('') + '<span>住客如果是來問進度，可以直接告訴他；真的要再加一份，照常送出。</span></div></div>' : '';
    if (dup.dataset.html !== dupHtml) { dup.innerHTML = dupHtml; dup.dataset.html = dupHtml; }
    const sum = document.getElementById('crSummary'); const s = summary(); if (sum.textContent !== s) sum.textContent = s;
    const btn = document.getElementById('crSend'); const n = count();
    btn.disabled = !canSend() && !st.sending;
    if (st.sending) { btn.setAttribute('aria-busy', 'true'); btn.innerHTML = '<span class="cr-spin" aria-hidden="true"></span>送出 ' + n + ' 張單'; }
    else { btn.removeAttribute('aria-busy'); btn.textContent = n ? '送出 ' + n + ' 張單' : '送出'; }
  }

  /* 重畫之後把焦點放回剛才按的那一顆（同一個品項的「−」減到 0 時改放回格子本身），鍵盤操作才不會每按一次就掉回最上面。 */
  function update(focusKey) {
    if (!st) return;
    drawRoom(); drawGrid(); drawRest();
    const panel = document.querySelector('.cr-panel');
    if (st.sending) panel.setAttribute('data-sending', ''); else panel.removeAttribute('data-sending');
    ['crRoom', 'crText'].forEach((id) => { document.getElementById(id).readOnly = st.sending; });
    if (focusKey) {
      const el = document.querySelector('[data-f="' + focusKey + '"]') || document.querySelector('[data-f="add:' + focusKey.split(':')[1] + '"]');
      if (el) el.focus();
    }
  }

  function open() {
    if (st) return;
    opener = document.activeElement;
    st = { room: D.detailRoom(), qty: {}, text: '', textDept: hk, sending: false, key: null, keySig: null };
    shell(); bind();
    document.getElementById('crRoom').value = st.room;
    if (roomList.mode === 'loading' && !roomList.timer) roomList.timer = setTimeout(() => { roomList.mode = 'ok'; update(); }, 2500);
    update();
    if (st.room) document.querySelector('.cr-add').focus(); else document.getElementById('crRoom').focus();
  }
  /* 送出還沒有結果時櫃檯關不掉面板：關掉就看不到這次有沒有開成，櫃檯可能以為沒送出又重開一張。 */
  function close() {
    if (!st || st.sending) return;
    st = null;
    const host = document.getElementById('createHost'); if (host) host.remove();
    if (opener && opener.isConnected) opener.focus();
  }

  function bind() {
    const host = document.getElementById('createHost');
    host.querySelector('.cr-scrim-btn').onclick = close;
    document.getElementById('crCancel').onclick = close;
    const room = document.getElementById('crRoom');
    room.addEventListener('input', () => { const v = digits(room.value); if (room.value !== v) room.value = v; st.room = v; update(); });
    const ta = document.getElementById('crText');
    ta.addEventListener('input', () => { const v = cut60(ta.value); if (ta.value !== v) ta.value = v; st.text = v; update(); });
    host.querySelector('.cr-panel').addEventListener('click', (e) => {
      if (st.sending) return;
      const b = e.target.closest('button'); if (!b) return;
      if (b.dataset.room) { st.room = b.dataset.room; room.value = st.room; update(); const first = document.querySelector('.cr-add'); if (first) first.focus(); return; }
      if (b.id === 'crRetry') { roomList.mode = 'ok'; update(); return; }
      const it = (k) => ITEMS.find((i) => i.key === k);
      if (b.dataset.add) { const i = it(b.dataset.add); st.qty[i.key] = Math.min(i.max, (st.qty[i.key] || 0) + 1); update(b.dataset.f); return; }
      if (b.dataset.minus) { const k = b.dataset.minus; st.qty[k] = Math.max(0, (st.qty[k] || 0) - 1); update(st.qty[k] ? b.dataset.f : 'add:' + k); return; }
      if (b.dataset.remove) { st.qty[b.dataset.remove] = 0; update('add:' + b.dataset.remove); return; }
      if (b.dataset.dept && b.closest('.cr-seg')) { st.textDept = b.dataset.dept; update(b.dataset.f); return; }
      if (b.id === 'crSend') submit();
    });
  }

  /* Esc 關最上面那一層：有提示對話框先關提示；送出中不關面板。Tab 只在面板裡繞。 */
  document.addEventListener('keydown', (e) => {
    if (!st) return;
    const alert = document.getElementById('alert');
    if (e.key === 'Escape') { if (alert) { const b = alert.querySelector('button'); if (b) b.click(); } else close(); return; }
    if (e.key === 'Tab' && !alert) {
      const f = Array.from(document.querySelectorAll('.cr-panel button:not([disabled]), .cr-panel input, .cr-panel textarea')).filter((el) => el.offsetParent !== null);
      if (!f.length) return;
      const first = f[0], last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  });
  document.addEventListener('click', (e) => { if (e.target.closest && e.target.closest('#btnCreate')) open(); });

  /* 冪等鍵跟著內容走：房號、品項數量、其他需求文字或部門一改，就算另一次送出。 */
  function signature() { return JSON.stringify([st.room, ITEMS.map((i) => st.qty[i.key] || 0), otherText(), otherText() ? st.textDept : '']); }

  function makeTasks() {
    const me = D.me(), room = st.room, ids = [];
    const push = (prefix, x) => { const t = Object.assign({ id: D.nextId(prefix), room, src: 'desk', by: me.name, state: 'pool', c: D.now() }, x); D.add([t]); ids.push(t.id); };
    picks().forEach((it) => {
      const n = st.qty[it.key];
      if (it.dept === hk) push('T-', { kind: 'amenity', dept: hk, item: it.key, title: it.key + ' ×' + n + (it.unit ? ' ' + it.unit : '') });
      else push('W-', { kind: 'repair', dept: eng, item: it.key, title: it.key + '有問題' });
    });
    if (otherText()) {
      if (st.textDept === hk) push('T-', { kind: 'other', dept: hk, title: otherText() });
      else push('W-', { kind: 'repair', dept: eng, title: otherText() });
    }
    return ids;
  }

  /* 後端明確拒絕時這次什麼都沒開，換掉冪等鍵；沒收到回應時保留，內容沒改再送才拿得到同一次的結果。 */
  function fail(kind, room, n) {
    st.sending = false;
    if (kind !== 'net') { st.key = null; st.keySig = null; }
    update();
    const body = {
      room: room + ' 不是這家飯店的房號，這 ' + n + ' 張單都還沒開出。請確認房號後再送出。',
      role: '這個帳號不能代開單，這 ' + n + ' 張單都還沒開出。請改用櫃檯、主管或值班主管的帳號。',
      invalid: room + ' 的 ' + n + ' 張單都還沒開出，內容不符合規定。請檢查品項與其他需求後再送出。',
      net: room + ' 的 ' + n + ' 張單都還沒開出。送出失敗，請稍後再試一次。'
    }[kind];
    D.showAlert('沒有送出', body, D.ok(() => { const b = document.getElementById('crSend'); if (b) b.focus(); }));
  }

  function succeed(ids) {
    st = null;
    const host = document.getElementById('createHost'); if (host) host.remove();
    D.afterCreate(ids);
  }

  function submit() {
    if (!canSend()) return;
    const sig = signature();
    if (st.keySig !== sig) { st.key = 'k' + Math.random().toString(36).slice(2); st.keySig = sig; }
    const room = st.room, n = count(), key = st.key;
    st.sending = true; update();
    const mode = D.Q.get('create');
    setTimeout(() => {
      if (!st) return;
      if (mode === 'auth') { D.logout(); return; }
      if (mode === 'room' || mode === 'role' || mode === 'invalid' || mode === 'net') { fail(mode, room, n); return; }
      if (committed[key]) { succeed(committed[key]); return; }
      const ids = makeTasks();
      if (mode === 'lost') { committed[key] = ids; D.refresh(); fail('net', room, n); return; }
      succeed(ids);
    }, mode === 'slow' ? 2000 : 450);
  }
})();
