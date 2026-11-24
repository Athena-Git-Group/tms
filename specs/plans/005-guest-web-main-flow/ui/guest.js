/* 住客掃碼頁靜態雛形的示範資料與互動。資料只放在這個瀏覽器分頁，關掉分頁或在網址加 ?reset=1 就回到初始狀態。 */
(function () {
  'use strict';
  const Q = new URLSearchParams(location.search);
  const PAGE = document.body.dataset.page;
  const FAIL = Q.get('fail');
  const app = document.getElementById('app');
  const $ = (sel, root) => (root || document).querySelector(sel);
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  /* 私密瀏覽或預覽環境可能寫不進儲存區；讀寫失敗一律當成沒有存，雛形照樣能操作。 */
  const storage = (area) => ({
    get(k, d) { try { const v = window[area].getItem(k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { window[area].setItem(k, JSON.stringify(v)); } catch (e) { return; } }
  });
  const ss = storage('sessionStorage');
  const ls = storage('localStorage');

  const IC = {"chevron_back": "M14.8 4.3Q15.1 4 15.75 4Q16.2 4 16.5 4.3Q16.8 4.6 16.8 5.1Q16.8 5.5 16.5 5.8L10.4 11.9L16.5 18.1Q16.8 18.4 16.8 18.9Q16.8 19.4 16.5 19.7Q16.2 20 15.75 20Q15.1 20 14.8 19.7L7.45 12.5Q7.2 12.25 7.2 11.95Q7.2 11.75 7.45 11.5Z", "chevron_forward": "M9.2 4.3 16.55 11.5Q16.8 11.75 16.8 11.95Q16.8 12.25 16.55 12.5L9.2 19.7Q8.9 20 8.25 20Q7.8 20 7.5 19.7Q7.2 19.4 7.2 18.9Q7.2 18.4 7.5 18.1L13.6 11.9L7.5 5.8Q7.2 5.5 7.2 5.1Q7.2 4.6 7.5 4.3Q7.8 4 8.25 4Q8.9 4 9.2 4.3Z", "globe": "M19.04 19.04Q16.08 22 12 22Q7.92 22 4.96 19.04Q2 16.08 2 12Q2 7.92 4.96 4.96Q7.92 2 12 2Q16.08 2 19.04 4.96Q22 7.92 22 12Q22 16.08 19.04 19.04ZM9.09 6.79Q10.26 7.02 11.39 7.02V3.88Q10.08 4.35 9.09 6.79ZM12.56 3.88V7.02Q13.69 7.02 14.86 6.79Q13.97 4.39 12.56 3.88ZM14.68 4.16Q15.47 5.1 16.04 6.51Q16.84 6.23 17.54 5.85Q16.37 4.77 14.68 4.16ZM6.41 5.85Q7.26 6.27 7.96 6.51Q8.57 5 9.28 4.16Q7.68 4.72 6.41 5.85ZM16.98 11.44H20.26Q20.03 8.62 18.38 6.69Q17.4 7.26 16.37 7.59Q16.93 9.46 16.98 11.44ZM3.74 11.44H6.98Q7.02 9.46 7.59 7.59Q6.65 7.31 5.62 6.69Q3.97 8.62 3.74 11.44ZM12.56 11.44H15.8Q15.76 9.79 15.24 7.87Q14.11 8.1 12.56 8.2ZM8.2 11.44H11.39V8.2Q9.89 8.1 8.76 7.87Q8.29 9.46 8.2 11.44ZM3.74 12.56Q3.97 15.33 5.62 17.35Q6.65 16.74 7.59 16.46Q7.02 14.54 6.98 12.56ZM8.2 12.56Q8.29 14.58 8.76 16.18Q9.89 15.94 11.39 15.85V12.56ZM12.56 15.85Q14.11 15.94 15.24 16.18Q15.71 14.58 15.8 12.56H12.56ZM16.37 16.46Q17.31 16.74 18.34 17.35Q20.12 15.19 20.26 12.56H16.98Q16.93 14.54 16.37 16.46ZM9.09 17.26Q10.08 19.7 11.39 20.17V17.02Q10.26 17.02 9.09 17.26ZM12.56 17.02V20.17Q13.97 19.65 14.86 17.26Q13.69 17.02 12.56 17.02ZM6.46 18.15Q7.77 19.32 9.23 19.84Q8.62 19.14 7.96 17.54Q6.98 17.87 6.46 18.15ZM16.04 17.54Q15.43 19.04 14.72 19.84Q16.37 19.23 17.54 18.2Q16.84 17.82 16.04 17.54Z", "checkmark": "M10.12 19.74Q9.57 19.74 9.15 19.23L4.45 13.23Q4.2 12.93 4.2 12.55Q4.2 11.7 5.09 11.7Q5.55 11.7 5.85 12.17L10.08 17.67L18.19 4.73Q18.49 4.26 19 4.26Q19.8 4.26 19.8 5.07Q19.8 5.4 19.59 5.74L11.01 19.23Q10.71 19.74 10.12 19.74Z", "minus": "M5.57 12.77Q5.29 12.77 5.04 12.55Q4.8 12.33 4.8 12Q4.8 11.67 5.04 11.45Q5.29 11.23 5.57 11.23H18.39Q18.71 11.23 18.96 11.47Q19.2 11.72 19.2 12Q19.2 12.28 18.96 12.53Q18.71 12.77 18.39 12.77Z", "plus": "M11.24 18.48V12.76H5.68Q5.4 12.76 5.16 12.54Q4.92 12.32 4.92 12Q4.92 11.68 5.16 11.46Q5.4 11.24 5.68 11.24H11.24V5.52Q11.24 5.24 11.46 5.02Q11.68 4.8 12 4.8Q12.32 4.8 12.54 5.02Q12.76 5.24 12.76 5.52V11.24H18.28Q18.6 11.24 18.84 11.48Q19.08 11.72 19.08 12Q19.08 12.28 18.84 12.52Q18.6 12.76 18.28 12.76H12.76V18.48Q12.76 18.76 12.54 18.98Q12.32 19.2 12 19.2Q11.68 19.2 11.46 18.98Q11.24 18.76 11.24 18.48Z", "clock": "M12.02 22Q7.91 22 4.97 19.05Q2.02 16.11 2.02 12Q2.02 7.89 4.95 4.95Q7.87 2 11.98 2Q16.04 2 19.01 4.97Q21.98 7.94 21.98 12Q21.98 16.11 19.03 19.05Q16.09 22 12.02 22ZM6.13 17.89Q8.55 20.31 12.02 20.31Q15.49 20.31 17.91 17.89Q20.33 15.47 20.33 12Q20.33 8.53 17.89 6.11Q15.45 3.69 11.98 3.69Q8.51 3.69 6.11 6.11Q3.71 8.53 3.71 12Q3.71 15.47 6.13 17.89ZM6.86 13.05Q6.18 13.05 6.18 12.37Q6.18 12.09 6.38 11.89Q6.59 11.68 6.86 11.68H11.34V5.79Q11.34 5.11 11.98 5.11Q12.25 5.11 12.46 5.31Q12.66 5.52 12.66 5.79V12.37Q12.66 13.05 11.98 13.05Z", "checkmark_circle_fill": "M12.02 22Q7.91 22 4.97 19.05Q2.02 16.11 2.02 12Q2.02 7.89 4.95 4.95Q7.87 2 11.98 2Q16.04 2 19.01 4.97Q21.98 7.94 21.98 12Q21.98 16.11 19.03 19.05Q16.09 22 12.02 22ZM11.47 16.52 16.45 8.71Q16.73 8.35 16.73 8.07Q16.73 7.75 16.45 7.53Q16.18 7.3 15.86 7.3Q15.45 7.3 15.17 7.75L10.61 15.06L8.28 12Q8 11.59 7.5 11.59Q7.18 11.59 6.95 11.84Q6.73 12.09 6.73 12.41Q6.73 12.73 6.95 13.05L9.79 16.52Q10.2 17.02 10.65 17.02Q11.11 17.02 11.47 16.52Z", "exclamationmark_triangle_fill": "M4.36 21.21Q3.32 21.21 2.66 20.53Q2 19.85 2 18.85Q2 18.17 2.32 17.67L9.98 3.97Q10.66 2.79 12.02 2.79Q13.38 2.79 14.06 3.97L21.68 17.67Q22 18.17 22 18.85Q22 19.85 21.34 20.53Q20.68 21.21 19.64 21.21ZM12.02 14.54Q12.79 14.54 12.79 13.72L12.98 8.96Q12.98 8.55 12.68 8.28Q12.39 8.01 11.98 8.01Q11.07 8.01 11.07 8.92L11.21 13.72Q11.21 14.54 12.02 14.54ZM11.27 17.83Q11.61 18.12 12.02 18.12Q12.43 18.12 12.77 17.83Q13.11 17.53 13.11 17.08Q13.11 16.63 12.79 16.33Q12.48 16.04 12.02 16.04Q11.57 16.04 11.25 16.33Q10.93 16.63 10.93 17.08Q10.93 17.53 11.27 17.83Z", "info_circle_fill": "M12.02 22Q7.91 22 4.97 19.05Q2.02 16.11 2.02 12Q2.02 7.89 4.95 4.95Q7.87 2 11.98 2Q16.04 2 19.01 4.97Q21.98 7.94 21.98 12Q21.98 16.11 19.03 19.05Q16.09 22 12.02 22ZM10.65 7.25Q10.65 7.75 11.02 8.14Q11.38 8.53 11.93 8.53Q12.48 8.53 12.82 8.16Q13.16 7.8 13.16 7.25Q13.16 6.7 12.82 6.34Q12.48 5.97 11.93 5.97Q11.38 5.97 11.02 6.34Q10.65 6.7 10.65 7.25ZM10.29 17.39H14.26Q14.99 17.39 14.99 16.7Q14.99 16.43 14.79 16.22Q14.58 16.02 14.26 16.02H13.07V11.04Q13.07 10.17 12.3 10.17H10.42Q9.74 10.17 9.74 10.81Q9.74 11.54 10.42 11.54H11.52V16.02H10.29Q9.97 16.02 9.76 16.22Q9.56 16.43 9.56 16.7Q9.56 17.39 10.29 17.39Z", "phone": "M16.89 22Q12.21 22 7.11 16.89Q2 11.79 2 7.11Q2 4.58 3.62 3.1Q3.67 3.05 3.77 2.98Q3.86 2.91 3.91 2.86Q4.82 2 5.91 2Q7.06 2 7.97 3.34L10.02 6.3Q11.12 7.92 9.88 9.16L9.11 9.92Q8.78 10.26 8.97 10.59Q9.54 11.55 10.97 12.93Q12.45 14.41 13.36 14.98Q13.69 15.22 14.03 14.89L14.84 14.08Q16.08 12.84 17.7 13.93L20.66 16.03Q22 16.94 22 18.09Q22 19.14 21.09 20.09L20.85 20.33Q19.33 22 16.89 22ZM16.89 20.42Q18.56 20.42 19.66 19.18Q19.66 19.14 19.73 19.09Q19.8 19.04 19.85 18.99Q20.23 18.61 20.23 18.09Q20.23 17.56 19.85 17.32L16.84 15.32Q16.32 14.98 15.89 15.41L14.98 16.32Q13.89 17.42 12.69 16.46Q11.36 15.46 9.88 13.98Q8.68 12.79 7.54 11.26Q6.63 10.07 7.68 9.02L8.59 8.11Q8.97 7.73 8.63 7.11L6.68 4.15Q6.39 3.72 5.91 3.72Q5.39 3.72 4.96 4.15Q4.91 4.2 4.77 4.29Q3.58 5.34 3.58 7.11Q3.53 9.02 4.86 11.36Q6.2 13.69 8.3 15.79Q13.07 20.47 16.89 20.42Z", "star": "M6.14 20.91Q5.66 20.51 5.94 19.59L7.74 14.28L3.15 10.97Q2.31 10.41 2.55 9.78Q2.75 9.18 3.75 9.18L9.33 9.22L11.05 3.87Q11.37 2.92 12.01 2.92Q12.64 2.92 12.96 3.87L14.68 9.22L20.26 9.18Q21.26 9.18 21.46 9.78Q21.66 10.45 20.86 10.97L16.27 14.28L18.07 19.59Q18.35 20.51 17.87 20.91Q17.31 21.34 16.51 20.71L12.01 17.4L7.5 20.71Q6.7 21.34 6.14 20.91ZM7.38 19.23Q7.42 19.23 7.46 19.19L11.57 16.08Q12.01 15.76 12.45 16.08L16.55 19.19Q16.63 19.27 16.63 19.23V19.15L14.92 14.24Q14.72 13.73 15.16 13.45L19.43 10.49L19.47 10.45Q19.47 10.41 19.39 10.41L14.24 10.49Q13.68 10.49 13.52 10.02L12.05 5.07Q12.05 4.99 12.01 4.99Q11.97 5.03 11.97 5.07L10.49 10.02Q10.33 10.49 9.77 10.49L4.63 10.41Q4.55 10.41 4.55 10.45L4.59 10.49L8.86 13.45Q9.29 13.73 9.09 14.24L7.38 19.15Z", "star_fill": "M6.14 20.91Q5.66 20.51 5.94 19.59L7.74 14.28L3.15 10.97Q2.31 10.41 2.55 9.78Q2.75 9.18 3.75 9.18L9.33 9.22L11.05 3.87Q11.37 2.92 12.01 2.92Q12.64 2.92 12.96 3.87L14.68 9.22L20.26 9.18Q21.26 9.18 21.46 9.78Q21.66 10.45 20.86 10.97L16.27 14.28L18.07 19.59Q18.35 20.51 17.87 20.91Q17.31 21.34 16.51 20.71L12.01 17.4L7.5 20.71Q6.7 21.34 6.14 20.91Z", "text_bubble": "M7.59 21.41Q6.58 21.41 6.58 20.22V17.89H6.15Q4.12 17.89 3.06 16.81Q2 15.73 2 13.74V6.75Q2 4.75 3.06 3.67Q4.12 2.59 6.15 2.59H17.85Q19.88 2.59 20.94 3.67Q22 4.75 22 6.75V13.74Q22 15.73 20.94 16.81Q19.88 17.89 17.85 17.89H12.04L8.91 20.69Q8.14 21.41 7.59 21.41ZM7.97 19.71 10.86 16.83Q11.28 16.41 11.96 16.41H17.85Q20.56 16.41 20.56 13.69V6.75Q20.56 4.08 17.85 4.08H6.15Q3.44 4.08 3.44 6.75V13.69Q3.44 16.41 6.15 16.41H7.25Q7.97 16.41 7.97 17.08ZM7.38 7.93Q6.87 7.93 6.87 7.42Q6.87 6.87 7.38 6.87H16.62Q17.17 6.87 17.17 7.42Q17.17 7.93 16.62 7.93ZM7.38 10.9Q6.87 10.9 6.87 10.35Q6.87 9.84 7.38 9.84H16.62Q17.17 9.84 17.17 10.35Q17.17 10.9 16.62 10.9ZM7.38 13.86Q6.87 13.86 6.87 13.31Q6.87 12.81 7.38 12.81H13.4Q13.91 12.81 13.91 13.31Q13.91 13.86 13.4 13.86Z", "chat_bubble_2": "M7.3 15.42Q8.03 15.14 8.8 15.28Q9.53 15.37 9.9 15.37Q12.5 15.37 14.33 13.89Q16.16 12.4 16.16 10.39Q16.16 8.39 14.33 6.92Q12.5 5.46 9.9 5.46Q7.3 5.46 5.49 6.92Q3.69 8.39 3.69 10.39Q3.69 12.59 5.65 14.05L5.84 14.14L5.93 14.23H5.88H5.97L6.02 14.32Q6.75 14.91 6.93 15.55Q7.02 15.51 7.14 15.46Q7.25 15.42 7.3 15.42ZM8.53 15.28Q8.71 15.33 8.89 15.33Q8.71 15.33 8.53 15.28ZM2 10.39Q2 7.65 4.31 5.71Q6.61 3.77 9.9 3.77Q13.19 3.77 15.49 5.71Q17.8 7.65 17.8 10.39Q17.8 13.13 15.49 15.1Q13.19 17.06 9.9 17.06L8.53 16.92Q7.98 16.79 6.86 17.49Q5.74 18.2 4.56 18.48Q4.19 18.57 4.15 18.48Q4.1 18.39 4.37 18.07Q4.97 17.43 5.22 16.79Q5.47 16.15 5.06 15.65Q4.74 15.46 4.65 15.37Q2 13.41 2 10.39ZM13.23 17.02Q13.64 17.06 14.1 17.06Q14.42 17.06 15.15 16.97Q16.06 16.83 16.7 17.06Q16.75 17.06 16.79 17.11Q16.98 17.2 17.07 17.24Q17.3 16.47 17.94 15.96L18.03 15.92L18.07 15.87Q18.12 15.87 18.16 15.83L18.35 15.69Q20.31 14.23 20.31 12.08Q20.31 10.3 18.85 8.93Q18.8 8.93 18.8 8.89Q18.8 8.84 18.76 8.8Q18.16 8.29 18.67 7.75L18.71 7.7L18.76 7.61Q19.35 7.02 19.9 7.61L19.95 7.65Q22 9.57 22 12.08Q22 15.05 19.31 17.06L18.94 17.33Q18.53 17.79 18.76 18.45Q18.99 19.12 19.58 19.75Q19.76 19.98 19.81 20.07Q19.85 20.17 19.79 20.21Q19.72 20.26 19.44 20.17Q18.26 19.89 17.14 19.18Q16.02 18.48 15.47 18.61Q14.56 18.7 14.1 18.7Q13.42 18.7 13.1 18.66Q12.27 18.57 12.37 17.79V17.7Q12.55 16.92 13.23 17.02Z", "cube_box": "M4.03 17.73Q2.71 17 2.71 15.42V8.15Q2.71 6.79 3.86 6.15L10.31 2.47Q11.98 1.53 13.65 2.47L20.14 6.15Q21.29 6.79 21.29 8.15V15.42Q21.29 17 19.97 17.73L12.79 21.79Q11.98 22.21 11.21 21.79ZM16.29 8.75 18.99 7.26 13 3.84Q11.98 3.28 11 3.84L9.29 4.78ZM11.98 11.19 14.76 9.65 7.75 5.68 5.01 7.26ZM4.88 16.44 11.21 20.03V12.56L4.2 8.54V15.38Q4.2 16.06 4.88 16.44ZM19.12 16.44Q19.8 16.06 19.8 15.38V8.54L12.75 12.56V20.03Z", "hammer": "M1.72 20.27Q0.6 19.16 1.89 17.92L11.63 8.94Q11.29 8.34 11.59 7.62L12.15 6.25Q10.44 5.05 8.21 5.61Q7.36 5.82 7.02 5.22Q6.59 4.54 7.19 3.94Q9.15 1.85 11.93 1.89Q14.71 1.93 16.98 4.2L19.07 6.29Q19.8 6.98 19.63 7.75L19.37 8.73L19.97 9.33L20.4 9.24Q21.04 9.11 21.68 9.75L22.4 10.48Q23.17 11.21 22.45 11.93L19.58 14.76Q18.9 15.44 18.17 14.71L17.45 13.99Q16.89 13.43 16.93 12.75L16.98 12.28L16.38 11.68L15.44 11.89Q14.8 12.02 14.33 11.72L5.39 21.42Q4.15 22.71 3.04 21.6ZM8.56 4.28Q8.43 4.37 8.51 4.45Q8.56 4.5 8.68 4.45Q11.21 3.69 13.68 5.99L12.87 7.96Q12.7 8.26 13 8.56L14.88 10.48Q15.1 10.7 15.48 10.65L16.89 10.35L18.26 11.72L18.17 12.58Q18.13 12.88 18.34 13.09L18.9 13.64L21.29 11.25L20.78 10.7Q20.52 10.44 20.22 10.48L19.41 10.57L18 9.2L18.34 7.83Q18.39 7.45 18.17 7.23L15.86 4.92Q14.28 3.34 12.3 3.15Q10.31 2.96 8.56 4.28ZM2.83 19.63 3.64 20.48Q4.15 21 4.62 20.44L13.47 10.91L12.4 9.84L2.83 18.69Q2.31 19.12 2.83 19.63Z", "sparkles": "M11.51 6.26Q11.43 6.26 11.34 6.1Q11.14 4.99 10.91 4.77Q10.69 4.54 9.54 4.3Q9.38 4.3 9.38 4.13Q9.38 3.93 9.54 3.93Q10.65 3.72 10.87 3.48Q11.1 3.23 11.34 2.12Q11.43 2 11.51 2Q11.67 2 11.71 2.12Q11.96 3.27 12.16 3.5Q12.37 3.72 13.48 3.93Q13.64 3.93 13.64 4.13Q13.64 4.3 13.48 4.3Q12.37 4.5 12.14 4.73Q11.92 4.95 11.71 6.1Q11.63 6.26 11.51 6.26ZM7.29 12.29Q6.96 12.29 6.96 12Q6.75 10.24 6.39 9.87Q6.02 9.5 4.21 9.21Q3.89 9.13 3.89 8.93Q3.89 8.72 4.17 8.64Q6.02 8.31 6.39 7.92Q6.75 7.53 6.96 5.81Q6.96 5.57 7.29 5.57Q7.57 5.57 7.57 5.81Q7.82 7.61 8.21 8Q8.6 8.39 10.4 8.64Q10.65 8.72 10.65 8.93Q10.65 9.13 10.4 9.21Q8.56 9.54 8.19 9.93Q7.82 10.32 7.57 12.04Q7.57 12.29 7.29 12.29ZM13.23 22Q12.82 22 12.78 21.63Q12.29 18.02 11.28 17Q10.28 15.98 6.71 15.52Q6.3 15.44 6.3 15.11Q6.3 14.75 6.71 14.66Q10.28 14.25 11.3 13.23Q12.33 12.2 12.78 8.6Q12.86 8.19 13.23 8.19Q13.56 8.19 13.64 8.6Q14.09 12.2 15.11 13.23Q16.14 14.25 19.7 14.66Q20.11 14.75 20.11 15.11Q20.11 15.44 19.7 15.52Q16.14 15.93 15.11 16.98Q14.09 18.02 13.64 21.63Q13.56 22 13.23 22Z", "bubble_left": "M7.59 21.41Q6.58 21.41 6.58 20.22V17.89H6.15Q4.12 17.89 3.06 16.81Q2 15.73 2 13.74V6.75Q2 4.75 3.06 3.67Q4.12 2.59 6.15 2.59H17.85Q19.88 2.59 20.94 3.67Q22 4.75 22 6.75V13.74Q22 15.73 20.94 16.81Q19.88 17.89 17.85 17.89H12.04L8.91 20.69Q8.14 21.41 7.59 21.41ZM7.97 19.71 10.86 16.83Q11.28 16.41 11.96 16.41H17.85Q20.56 16.41 20.56 13.69V6.75Q20.56 4.08 17.85 4.08H6.15Q3.44 4.08 3.44 6.75V13.69Q3.44 16.41 6.15 16.41H7.25Q7.97 16.41 7.97 17.08Z", "car": "M19.66 4.76 22 10.91V19.24Q22 19.71 21.69 20.02Q21.38 20.33 20.91 20.33H19.76Q19.29 20.33 18.98 20.02Q18.67 19.71 18.67 19.24V18.67H5.33V19.24Q5.33 19.71 5.02 20.02Q4.71 20.33 4.24 20.33H3.09Q2.62 20.33 2.31 20.02Q2 19.71 2 19.24V10.91L4.34 4.76Q4.66 3.67 5.91 3.67H18.09Q19.34 3.67 19.66 4.76ZM4.71 14.86Q5.18 15.33 5.91 15.33Q6.64 15.33 7.1 14.86Q7.57 14.4 7.57 13.67Q7.57 12.94 7.1 12.47Q6.64 12 5.91 12Q5.18 12 4.71 12.47Q4.24 12.94 4.24 13.67Q4.24 14.4 4.71 14.86ZM16.9 14.86Q17.36 15.33 18.09 15.33Q18.82 15.33 19.29 14.86Q19.76 14.4 19.76 13.67Q19.76 12.94 19.29 12.47Q18.82 12 18.09 12Q17.36 12 16.9 12.47Q16.43 12.94 16.43 13.67Q16.43 14.4 16.9 14.86ZM4.24 9.76H19.76L18.09 5.33H5.91Z", "briefcase": "M4.77 20.64Q2 20.64 2 17.91V9.19Q2 6.42 4.77 6.42H6.7V5.88Q6.7 3.36 9.21 3.36H14.79Q17.35 3.36 17.35 5.88V6.42H19.23Q22 6.42 22 9.19V17.91Q22 20.64 19.23 20.64ZM8.08 5.75V6.42H15.92V5.75Q15.92 4.66 14.83 4.66H9.21Q8.08 4.66 8.08 5.75ZM3.43 9.23V11.62H20.57V9.23Q20.57 7.89 19.23 7.89H4.81Q3.43 7.89 3.43 9.23ZM4.81 19.21H19.23Q20.57 19.21 20.57 17.83V12.96H15.54V13.51Q15.54 14.68 14.41 14.68H9.63Q8.46 14.68 8.46 13.51V12.96H3.43V17.83Q3.43 19.21 4.81 19.21Z", "bed_double": "M2.66 18.64Q2 18.64 2 17.98V12.59Q2 10.48 4.07 10.28V7.55Q4.07 5.36 6.26 5.36H17.74Q19.93 5.36 19.93 7.55V10.28Q22 10.48 22 12.59V17.98Q22 18.64 21.34 18.64H20.87Q20.2 18.64 20.2 17.98V16.8Q20.12 16.84 19.66 16.84H4.34Q3.88 16.84 3.8 16.8V17.98Q3.8 18.64 3.13 18.64ZM6.14 8.8V10.28H11.22V8.8Q11.22 7.66 10.05 7.66H7.27Q6.14 7.66 6.14 8.8ZM12.78 8.8V10.28H17.86V8.8Q17.86 7.66 16.73 7.66H13.95Q12.78 7.66 12.78 8.8ZM3.64 15.63H20.36Q20.63 15.63 20.63 15.32V12.47Q20.63 12.04 20.34 11.77Q20.05 11.49 19.62 11.49H4.38Q3.37 11.49 3.37 12.47V15.32Q3.37 15.63 3.64 15.63Z", "list_bullet": "M5.31 7.63Q5 7.95 4.56 7.95Q4.13 7.95 3.81 7.63Q3.5 7.32 3.5 6.88Q3.5 6.45 3.81 6.14Q4.13 5.82 4.56 5.82Q5 5.82 5.31 6.14Q5.63 6.45 5.63 6.88Q5.63 7.32 5.31 7.63ZM8.3 7.59Q7.63 7.59 7.63 6.88Q7.63 6.22 8.3 6.22H19.79Q20.5 6.22 20.5 6.88Q20.5 7.2 20.3 7.4Q20.11 7.59 19.79 7.59ZM5.31 12.75Q5 13.06 4.56 13.06Q4.13 13.06 3.81 12.75Q3.5 12.43 3.5 12Q3.5 11.57 3.81 11.25Q4.13 10.94 4.56 10.94Q5 10.94 5.31 11.25Q5.63 11.57 5.63 12Q5.63 12.43 5.31 12.75ZM8.3 12.67Q7.63 12.67 7.63 12Q7.63 11.33 8.3 11.33H19.79Q20.5 11.33 20.5 12Q20.5 12.67 19.79 12.67ZM5.31 17.86Q5 18.18 4.56 18.18Q4.13 18.18 3.81 17.86Q3.5 17.55 3.5 17.12Q3.5 16.68 3.81 16.37Q4.13 16.05 4.56 16.05Q5 16.05 5.31 16.37Q5.63 16.68 5.63 17.12Q5.63 17.55 5.31 17.86ZM8.3 17.78Q7.63 17.78 7.63 17.12Q7.63 16.41 8.3 16.41H19.79Q20.11 16.41 20.3 16.6Q20.5 16.8 20.5 17.12Q20.5 17.78 19.79 17.78Z", "wifi": "M3.32 10.86 2.14 9.64Q1.89 9.35 2.1 9.14Q3.65 7.2 6.33 6Q9 4.8 12 4.8Q14.99 4.8 17.66 6Q20.34 7.2 21.89 9.14Q22.11 9.35 21.89 9.64L20.67 10.82Q20.38 11.12 20.13 10.86Q16.92 7.41 12 7.41Q7.15 7.41 3.91 10.86Q3.61 11.16 3.32 10.86ZM6.94 14.49 5.59 13.14Q5.38 12.88 5.55 12.67Q6.56 11.41 8.29 10.63Q10.02 9.85 12 9.85Q13.98 9.85 15.7 10.63Q17.43 11.41 18.44 12.67Q18.65 12.93 18.44 13.14L17.09 14.49Q16.8 14.78 16.5 14.49Q14.69 12.46 12 12.46Q9.3 12.46 7.53 14.44Q7.24 14.78 6.94 14.49ZM12 19.2Q11.74 19.2 11.32 18.78L9.21 16.76Q9 16.55 9.13 16.34Q10.18 14.91 12 14.91Q13.72 14.91 14.86 16.3Q15.03 16.55 14.82 16.76L12.71 18.78Q12.29 19.2 12 19.2Z", "xmark_circle_fill": "M12.02 22Q7.91 22 4.97 19.05Q2.02 16.11 2.02 12Q2.02 7.89 4.95 4.95Q7.87 2 11.98 2Q16.04 2 19.01 4.97Q21.98 7.94 21.98 12Q21.98 16.11 19.03 19.05Q16.09 22 12.02 22ZM8.51 16.34Q8.83 16.34 9.05 16.11L11.98 13.14L14.9 16.11Q15.13 16.34 15.49 16.34Q15.81 16.34 16.06 16.09Q16.32 15.84 16.32 15.52Q16.32 15.15 16.09 14.92L13.16 12L16.09 9.08Q16.36 8.89 16.36 8.48Q16.36 8.16 16.11 7.91Q15.86 7.66 15.49 7.66Q15.22 7.66 14.95 7.94L11.98 10.86L9.05 7.94Q8.83 7.71 8.51 7.71Q8.14 7.71 7.89 7.96Q7.64 8.21 7.64 8.53Q7.64 8.8 7.91 9.08L10.84 12L7.91 14.92Q7.64 15.2 7.64 15.52Q7.64 15.84 7.89 16.09Q8.14 16.34 8.51 16.34Z"};
  const icon = (name, cls) => '<svg class="tw-icon' + (cls ? ' ' + cls : '') + '" viewBox="0 0 24 24" aria-hidden="true"><path d="' + IC[name] + '"/></svg>';

  const LANGS = [['zh-Hant', '繁體中文', '繁中'], ['en', 'English', 'EN'], ['ja', '日本語', '日本語'], ['vi', 'Tiếng Việt', 'VI']];
  const LANG_KEY = 'guest.language';
  const SEND_LANG = { 'zh-Hant': 'zh-TW', en: 'en', ja: 'ja', vi: 'vi' };
  const detect = (b) => { const x = String(b || '').toLowerCase(); return x.indexOf('zh') === 0 ? 'zh-Hant' : x.indexOf('ja') === 0 ? 'ja' : x.indexOf('vi') === 0 ? 'vi' : 'en'; };
  let lang = ls.get(LANG_KEY, null);
  if (!LANGS.some((l) => l[0] === lang)) lang = detect(navigator.language);

  const C01 = '目前無法在這裡提出需求，需要什麼請直接找櫃檯。';
  const C02 = '點一下就報修';
  const C04 = '已送出 {1} 項，其餘的請稍後再送出，或用房內電話撥 9 找櫃檯。';
  const C05 = '在首頁選「備品補充」或「設備報修」，送出後進度會顯示在這裡。';
  const LIMIT_TITLE = '10 分鐘內最多送出 3 項';
  const LIMIT_N = '這次還能送出 {1} 項，請先減少 {2} 項，或稍後再送。急事請撥 9。';
  const LIMIT_0 = '請稍後再送出，或用房內電話撥 9 找櫃檯。';
  const LOCKED = '同仁已經接手。需要更改或取消，請用房內電話撥 9 找櫃檯。';
  const CANCEL_NOTE = '還沒有同仁接手前，都可以自己取消';
  const TOO_LATE = '同仁已經接手，無法取消。請撥 9 找櫃檯。';
  const NET_TOAST = '沒有送出，請檢查網路連線後再試一次。';

  /* 譯文只收原型字典已有的句子（依序英文、日文、越南文），以及把原型句子裡的姓名、時間拿掉後的句子；
     沒有出處的句子在別的語言下維持繁體中文，不自己編譯文。鍵裡「|」之後的字只用來分開同一句中文在原型裡的兩種譯法。 */
  const DICT = {
    '早安': ['Hello', 'おはようございます', 'Xin chào'],
    '午安': ['Hello', 'こんにちは', 'Xin chào'],
    '晚安': ['Hello', 'こんばんは', 'Xin chào'],
    '今天需要什麼？': ['How can we help?', '何をお手伝いしましょう？', 'Quý khách cần gì ạ?'],
    '{1} 號房': ['Room {1}', '{1}号室', 'Phòng {1}'],
    '房號 {1}': ['Room {1}', '客室 {1}', 'Phòng {1}'],
    '語言': ['Language', '言語', 'Ngôn ngữ'],
    '問問看：製冰機在哪裡？': ['Ask us — e.g. where’s the ice?', '例：製氷機はどこ？', 'Hỏi thử: máy làm đá ở đâu?'],
    '我的需求': ['My requests', 'リクエスト', 'Yêu cầu của tôi'],
    '全部 {1} 項': ['All {1}', 'すべて {1} 件', 'Tất cả {1}'],
    '進行中的需求': ['In progress', '対応中のリクエスト', 'Yêu cầu đang xử lý'],
    '查看': ['View', '見る', 'Xem'],
    '目前沒有進行中的需求': ['Nothing in progress', '対応中のリクエストはありません', 'Không có yêu cầu đang xử lý'],
    '查看紀錄': ['History', '履歴を見る', 'Xem lịch sử'],
    '已送出': ['Sent', '送信済み', 'Đã gửi'],
    '已受理': ['Received', '受付済み', 'Đã tiếp nhận'],
    '處理中': ['In progress', '対応中', 'Đang xử lý'],
    '已完成': ['Done', '完了', 'Hoàn tất'],
    '已取消': ['Cancelled', 'キャンセル済み', 'Đã hủy'],
    '櫃檯正在看您的需求': ['The front desk is reviewing it', 'フロントが内容を確認しています', 'Lễ tân đang xem yêu cầu của quý khách'],
    '櫃檯正在看您的需求|msg': ['The front desk is reviewing it', 'フロントが確認しています', 'Lễ tân đang xem yêu cầu của quý khách'],
    '{1}已收到 · {2} 送出': ['{1} has it · sent {2}', '{1}が受付 · {2} 送信', '{1} đã nhận · gửi lúc {2}'],
    '{1}已接手，馬上過去': ['{1} is on the way', '{1}が向かっています', '{1} đã nhận, sẽ đến ngay'],
    '{1}正在處理': ['{1} is working on it', '{1}が対応中', '{1} đang xử lý'],
    '房務': ['Housekeeping', '客室係', 'Buồng phòng'],
    '工務': ['Maintenance', '施設係', 'Kỹ thuật'],
    '櫃檯': ['Front Desk', 'フロント', 'Lễ tân'],
    '毛巾、礦泉水': ['Towels, water', 'タオル・お水', 'Khăn, nước'],
    '馬上送到房間': ['on the way', 'すぐお届け', 'giao ngay'],
    '東西壞了？': ['Broken?', '故障ですか？', 'Bị hỏng?'],
    '備品補充': ['Amenities', 'アメニティ', 'Bổ sung vật dụng'],
    '設備報修': ['Report a problem', '故障の連絡', 'Báo sửa thiết bị'],
    '服務': ['Services', 'サービス', 'Dịch vụ'],
    '晚點': ['Late', 'レイト', 'Trả phòng'],
    '退房': ['check-out', 'チェックアウト', 'muộn'],
    '寄放': ['Store', '荷物を', 'Gửi'],
    '行李': ['luggage', '預ける', 'hành lý'],
    '幫我': ['Book a', 'タクシーを', 'Gọi'],
    '叫車': ['taxi', '呼ぶ', 'taxi'],
    '住宿指南': ['Hotel guide', 'ご利用案内', 'Hướng dẫn lưu trú'],
    '緊急狀況請用房內電話撥 9 找櫃檯': ['In an emergency, dial 9 from the room phone', '緊急時は客室の電話で 9 番へ', 'Khẩn cấp: bấm 9 trên điện thoại trong phòng'],
    '返回': ['Back', '戻る', 'Quay lại'],
    '返回|keep': ['Keep', '戻る', 'Quay lại'],
    '選好數量一次送出，每一項會分別交給房務。': ['Pick quantities and send once. Each item goes to Housekeeping separately.', '数量を選んでまとめて送信。品目ごとに客室係へ届きます。', 'Chọn số lượng rồi gửi một lần. Mỗi món sẽ được chuyển riêng cho buồng phòng.'],
    '最多 {1}': ['Up to {1}', '最大 {1}', 'Tối đa {1}'],
    '送出 {1} 項': ['Send {1} items', '{1} 件を送信', 'Gửi {1} món'],
    '送出 1 項': ['Send 1 item', '1 件を送信', 'Gửi 1 món'],
    '選擇數量': ['Choose quantities', '数量を選択', 'Chọn số lượng'],
    '減少{1}': ['Fewer {1}', '{1}を減らす', 'Giảm {1}'],
    '增加{1}': ['More {1}', '{1}を増やす', 'Thêm {1}'],
    [CANCEL_NOTE]: ['You can cancel until a staff member takes it', 'スタッフが対応を始めるまで取り消せます', 'Quý khách có thể tự hủy trước khi nhân viên nhận'],
    [LIMIT_TITLE]: ['Up to 3 requests every 10 minutes', '10分間に送れるのは3件までです', 'Tối đa 3 yêu cầu mỗi 10 phút'],
    [LIMIT_N]: ['You can send {1} more now. Remove {2}, or try again later. Dial 9 if urgent.', '今は残り {1} 件です。{2} 件減らすか、後でもう一度。急ぎは 9 番へ。', 'Lần này còn gửi được {1} món. Vui lòng bớt {2} món hoặc gửi lại sau. Việc gấp xin bấm 9.'],
    [LIMIT_0]: ['Please try again later, or dial 9 for the front desk.', 'しばらくしてから送信するか、9 番でフロントへ。', 'Vui lòng gửi lại sau, hoặc bấm 9 để gọi lễ tân.'],
    '哪裡有問題？': ['What’s wrong?', 'どうしましたか？', 'Có vấn đề gì?'],
    '冷氣不冷': ['AC not cooling', 'エアコンが効かない', 'Máy lạnh không mát'],
    '電視無訊號': ['TV has no signal', 'テレビが映らない', 'TV không có tín hiệu'],
    '燈具故障': ['Light not working', '照明がつかない', 'Đèn bị hỏng'],
    '熱水不熱': ['No hot water', 'お湯が出ない', 'Không có nước nóng'],
    '網路連不上': ['Can’t get online', 'ネットにつながらない', 'Không vào được mạng'],
    '其他': ['Other', 'その他', 'Khác'],
    '補充說明（選填）': ['Details (optional)', '詳細（任意）', 'Mô tả thêm (không bắt buộc)'],
    '補充說明（必填）': ['Details (required)', '詳細（必須）', 'Mô tả thêm (bắt buộc)'],
    '例如：浴室那盞一直閃': ['e.g. the bathroom light keeps flickering', '例：浴室の照明が点滅している', 'Ví dụ: đèn phòng tắm cứ nhấp nháy'],
    '可以用任何語言輸入': ['Any language is fine', 'どの言語でも大丈夫です', 'Nhập bằng ngôn ngữ nào cũng được'],
    '送出報修': ['Send report', '故障を連絡', 'Gửi báo sửa'],
    '需求已送出': ['Request sent', '送信しました', 'Đã gửi yêu cầu'],
    '已送出 {1} 項需求': ['{1} requests sent', '{1} 件を送信しました', 'Đã gửi {1} yêu cầu'],
    '同仁接手後，進度會自動更新。': ['You’ll see updates here as staff work on it.', 'スタッフが対応すると、ここに自動で表示されます。', 'Khi nhân viên nhận, tiến độ sẽ tự cập nhật.'],
    '查看進度': ['View progress', '進捗を見る', 'Xem tiến độ'],
    '回首頁': ['Back to home', 'ホームへ', 'Về trang chủ'],
    '進行中': ['Active', '対応中', 'Đang xử lý'],
    '已結束': ['Finished', '完了', 'Đã kết thúc'],
    '還沒有送出任何需求': ['No requests yet', 'まだリクエストはありません', 'Chưa gửi yêu cầu nào'],
    '狀態會自動更新。退房後這份清單會一併清除。': ['Updates appear automatically. This list is cleared at check-out.', '状態は自動で更新されます。チェックアウト後に消去されます。', 'Trạng thái tự cập nhật. Danh sách sẽ được xóa khi trả phòng.'],
    '需求進度': ['Request', 'リクエスト', 'Tiến độ yêu cầu'],
    '{1} 送出 · {2}': ['Sent {1} · {2}', '{1} 送信 · {2}', 'Gửi lúc {1} · {2}'],
    '等櫃檯確認': ['Waiting for the front desk', 'フロントの確認待ち', 'Đang chờ lễ tân xác nhận'],
    '{1}收到': ['{1} has it', '{1}が受付', '{1} đã nhận'],
    '同仁接手後開始': ['Starts when staff take it', '担当が決まり次第開始', 'Bắt đầu khi nhân viên nhận'],
    '{1}已接手': ['{1} took it', '{1}が担当', '{1} đã nhận'],
    '完成後可以給我們評分': ['Rate us when it’s done', '完了後に評価できます', 'Hoàn tất xong có thể đánh giá'],
    '這個需求已取消': ['This request was cancelled', 'このリクエストは取り消されました', 'Yêu cầu này đã bị hủy'],
    '如果還需要，可以回首頁重新送出。': ['If you still need it, send it again from Home.', '必要ならホームからもう一度送信してください。', 'Nếu vẫn cần, quý khách có thể gửi lại từ Trang chủ.'],
    '櫃檯回覆': ['Front desk reply', 'フロントからの返信', 'Lễ tân trả lời'],
    '會交給負責的同仁，或直接在這裡回覆您。': ['We’ll pass it to the right team, or reply here.', '担当へ渡すか、ここで返信します。', 'Chúng tôi sẽ chuyển cho bộ phận phụ trách, hoặc trả lời ngay tại đây.'],
    '已完成|msg': ['Done', '完了しました', 'Hoàn tất'],
    '已處理完成。': ['All done.', '対応が完了しました。', 'Đã xử lý xong.'],
    '這次服務還滿意嗎？': ['How did we do?', 'ご満足いただけましたか？', 'Quý khách hài lòng với dịch vụ chứ?'],
    '為這次服務評分': ['Rate this service', 'このサービスを評価', 'Đánh giá dịch vụ lần này'],
    '{1} 顆星': ['{1} stars', '星 {1}', '{1} sao'],
    '點星星評分': ['Tap to rate', '星をタップ', 'Chạm vào sao để đánh giá'],
    '很不滿意': ['Very poor', 'とても不満', 'Rất không hài lòng'],
    '不滿意': ['Poor', '不満', 'Không hài lòng'],
    '普通': ['Okay', 'ふつう', 'Bình thường'],
    '滿意': ['Good', '満足', 'Hài lòng'],
    '非常滿意': ['Excellent', 'とても満足', 'Rất hài lòng'],
    '送出評分': ['Submit rating', '評価を送信', 'Gửi đánh giá'],
    '謝謝您的評分': ['Thanks for rating', '評価ありがとうございます', 'Cảm ơn quý khách đã đánh giá'],
    '取消這個需求': ['Cancel request', 'リクエストを取り消す', 'Hủy yêu cầu này'],
    [LOCKED]: ['Staff are already on it. To change or cancel, dial 9.', 'スタッフが対応中です。変更・取り消しは 9 番へ。', 'Nhân viên đã nhận. Muốn thay đổi hoặc hủy, vui lòng bấm 9 trên điện thoại trong phòng.'],
    '取消這個需求？': ['Cancel this request?', 'このリクエストを取り消しますか？', 'Hủy yêu cầu này?'],
    '「{1}」會從處理清單移除。': ['“{1}” will be removed from the queue.', '「{1}」を対応リストから削除します。', '“{1}” sẽ bị xóa khỏi danh sách xử lý.'],
    '取消需求': ['Cancel request', '取り消す', 'Hủy yêu cầu'],
    '已取消需求': ['Request cancelled', '取り消しました', 'Đã hủy yêu cầu'],
    [TOO_LATE]: ['Staff already took it, so it can’t be cancelled. Dial 9.', 'すでに対応中のため取り消せません。9 番へ。', 'Nhân viên đã nhận nên không thể hủy. Vui lòng bấm 9.'],
    '請洽櫃檯': ['Please contact the front desk', 'フロントへお問い合わせください', 'Vui lòng liên hệ lễ tân'],
    '櫃檯（24 小時）': ['Front desk (24 h)', 'フロント（24 時間）', 'Lễ tân (24 giờ)'],
    '房內電話撥 9': ['Dial 9 from the room phone', '客室の電話で 9 番', 'Bấm 9 trên điện thoại trong phòng'],
    '重試': ['Retry', '再試行', 'Thử lại']
  };
  const fill = (s, a) => String(s).replace(/\{(\d)\}/g, (m, i) => (a[i - 1] !== undefined ? a[i - 1] : ''));
  function t(key) {
    const args = [].slice.call(arguments, 1);
    const row = DICT[key];
    const i = ['en', 'ja', 'vi'].indexOf(lang);
    return fill(i >= 0 && row && row[i] ? row[i] : key.split('|')[0], args);
  }

  const ROOM = '311';
  const MAX = 5;
  const MENU = ['衣架', '毛巾', '牙刷組', '礦泉水', '吹風機'].map((x) => ({ key: x, label: x }));
  const BREAKFAST = { key: 'breakfast', title: '早餐', body: '一樓餐廳\n06:30–10:00' };
  const INFO_SETS = {
    three: [BREAKFAST, { key: 'wifi', title: 'Wi‑Fi', body: 'Wi‑Fi 名稱 Athena-Guest，密碼是房號加姓氏拼音。' }, { key: 'checkout', title: '退房時間', body: '中午 12:00 前' }],
    none: [],
    blank: [BREAKFAST, { key: 'untitled', title: '', body: '這一頁沒有標題。' }, { key: 'pool', title: '游泳池', body: '' }]
  };
  const min = (hm) => { const p = hm.split(':'); return Number(p[0]) * 60 + Number(p[1]); };
  const hm = (m) => String(Math.floor(m / 60) % 24).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
  const task = (id, title, type, state, dept, at, extra) => Object.assign({ id, title, type, state, dept, at: min(at), answer: null, rating: null, seq: 0 }, extra || {});
  const SEEDS = {
    three: () => [
      task('r1', '毛巾', 'amenity', 'done', 'housekeeping', '09:30'),
      task('r2', '礦泉水 ×2', 'amenity', 'pool', 'housekeeping', '14:05'),
      task('r3', '冷氣不冷：風量很小', 'other', 'triage', 'frontdesk', '14:12')
    ],
    new: () => [],
    ended: () => [
      task('r1', '毛巾', 'amenity', 'done', 'housekeeping', '09:30', { rating: 5 }),
      task('r2', '枕頭', 'amenity', 'cancelled', 'housekeeping', '10:02')
    ],
    all: () => [
      task('long', '冷氣不冷：房間冷氣開到最強還是不冷，而且一直有滴水聲，床邊地毯都濕了', 'other', 'triage', 'frontdesk', '14:01'),
      task('doing', '礦泉水 ×2', 'amenity', 'doing', 'housekeeping', '13:55'),
      task('pool', '衣架', 'amenity', 'pool', 'housekeeping', '13:45'),
      task('triage', '毛巾', 'other', 'triage', 'frontdesk', '13:40'),
      task('accepted', '牙刷組', 'amenity', 'accepted', 'housekeeping', '13:20'),
      task('deferred', '吹風機', 'amenity', 'deferred', 'housekeeping', '12:50'),
      task('answer', '冷氣不冷：風量很小', 'other', 'done', 'frontdesk', '12:05', { answer: '已請工務上去看' }),
      task('interrupted', '浴袍', 'amenity', 'interrupted', 'housekeeping', '11:30'),
      task('cancelled', '枕頭', 'amenity', 'cancelled', 'housekeeping', '10:40'),
      task('done', '拖鞋', 'amenity', 'done', 'housekeeping', '10:15'),
      task('inspected', '打掃', 'cleaning', 'inspected', 'housekeeping', '09:40'),
      task('closed', '燈具故障', 'repair', 'closed', 'engineering', '08:50')
    ]
  };

  const KEY = 'guest-proto';
  function load() {
    let s = Q.get('reset') === '1' ? null : ss.get(KEY, null);
    const want = SEEDS[Q.get('seed')] ? Q.get('seed') : null;
    if (!s || (want && want !== s.seed)) s = { seed: want || 'three', tasks: null, seq: 0, clock: min('14:30'), info: 'three', live: '0', theme: 'light', route: '' };
    if (!s.tasks) s.tasks = SEEDS[s.seed]();
    if (/^\d{1,2}:\d{2}$/.test(Q.get('at') || '')) s.clock = min(Q.get('at'));
    if (INFO_SETS[Q.get('info')]) s.info = Q.get('info');
    if (Q.get('live') !== null) s.live = Q.get('live');
    if (Q.get('theme') !== null) s.theme = Q.get('theme') === 'dark' ? 'dark' : 'light';
    if (Q.get('route') !== null) s.route = Q.get('route');
    ss.set(KEY, s);
    return s;
  }
  const S = load();
  const save = () => ss.set(KEY, S);
  const sorted = () => S.tasks.slice().sort((a, b) => (b.at - a.at) || (b.seq - a.seq));
  const infoPages = () => INFO_SETS[S.info] || [];
  function create(fields) {
    S.seq += 1;
    const x = Object.assign({ id: 'n' + S.seq, at: S.clock, answer: null, rating: null, seq: S.seq }, fields);
    S.tasks.push(x);
    save();
    return x;
  }
  /* 後端的 10 分鐘 3 筆是整間房一起算，住客頁只看得到自己這次住宿的需求，所以這個估算可能偏高；偏高時由後端的拒絕接手。 */
  const quotaLeft = () => 3 - S.tasks.filter((x) => x.at > S.clock - 10).length;

  const STAGE_OF = { triage: 'sent', pool: 'received', accepted: 'received', deferred: 'received', doing: 'working', interrupted: 'working', done: 'finished', inspected: 'finished', closed: 'finished', cancelled: 'cancelled' };
  const STAGE = {
    sent: { label: '已送出', tone: '', step: 0, active: true },
    received: { label: '已受理', tone: 'accepted', step: 1, active: true },
    working: { label: '處理中', tone: 'doing', step: 2, active: true },
    finished: { label: '已完成', tone: 'done', step: 3, active: false },
    cancelled: { label: '已取消', tone: '', step: null, active: false }
  };
  const STEPS = ['已送出', '已受理', '處理中', '已完成'];
  const DEPT = { housekeeping: '房務', engineering: '工務', frontdesk: '櫃檯' };
  const TYPE_ICON = { amenity: 'cube_box', repair: 'hammer', cleaning: 'sparkles', other: 'bubble_left' };
  const WORDS = ['很不滿意', '不滿意', '普通', '滿意', '非常滿意'];
  const stage = (x) => STAGE[STAGE_OF[x.state]];
  const canCancel = (x) => x.state === 'triage' || x.state === 'pool';
  const isLocked = (x) => stage(x).active && !canCancel(x);
  const canRate = (x) => (x.state === 'done' || x.state === 'inspected') && x.rating == null && !x.answer;
  const showTrack = (x) => x.state !== 'cancelled' && !x.answer;
  const deptOf = (x) => t(DEPT[x.dept] || '櫃檯');
  function statusMessage(x) {
    if (x.state === 'cancelled') return { tone: 'neutral', icon: 'info_circle_fill', title: t('這個需求已取消'), body: t('如果還需要，可以回首頁重新送出。') };
    if (x.answer) return { tone: '', icon: 'text_bubble', title: t('櫃檯回覆'), body: x.answer };
    if (x.state === 'triage') return { tone: '', icon: 'info_circle_fill', title: t('櫃檯正在看您的需求|msg'), body: t('會交給負責的同仁，或直接在這裡回覆您。') };
    if (stage(x).step === 3) return { tone: 'success', icon: 'checkmark_circle_fill', title: t('已完成|msg'), body: t('已處理完成。') };
    return null;
  }
  function stepMeta(x, k) {
    const s = stage(x).step;
    if (k === 1) return x.state === 'triage' ? t('等櫃檯確認') : t('{1}收到', deptOf(x));
    if (k === 2) {
      if (s === 3) return '';
      if (canCancel(x)) return t('同仁接手後開始');
      return s === 1 ? t('{1}已接手', deptOf(x)) : t('{1}正在處理', deptOf(x));
    }
    if (k === 3) return s === 3 ? '' : t('完成後可以給我們評分');
    return '';
  }
  function cardLine(x) {
    if (x.state === 'triage') return t('櫃檯正在看您的需求');
    if (x.state === 'pool') return t('{1}已收到 · {2} 送出', deptOf(x), hm(x.at));
    return stage(x).step === 1 ? t('{1}已接手，馬上過去', deptOf(x)) : t('{1}正在處理', deptOf(x));
  }
  const badge = (x) => { const g = stage(x); return '<span class="tw-badge' + (g.tone ? ' tw-badge--' + g.tone : '') + '">' + t(g.label) + '</span>'; };

  const view = { menu: false, qty: {}, busy: false, notice: null, chip: null, note: '', stars: 0, dlg: false, rating: false, cancelling: false, entry: Q.get('state') || 'unavailable' };
  const pageTitle = (name) => (name ? name + ' · ' : '') + t('{1} 號房', ROOM);
  function header(title, back) {
    return '<header class="tw-header" id="hdr"><a class="tw-btn tw-btn--plain" id="back" href="' + back + '" aria-label="' + esc(t('返回')) + '">' + icon('chevron_back') + '</a>' +
      '<div class="tw-header__text"><h1 class="tw-header__title">' + esc(title) + '</h1><span class="tw-header__room">' + esc(t('房號 {1}', ROOM)) + '</span></div></header>';
  }
  function notice() {
    const n = view.notice;
    if (!n) return '';
    return '<div class="tw-status tw-status--' + n.tone + '" role="alert">' + icon(n.tone === 'error' ? 'xmark_circle_fill' : 'exclamationmark_triangle_fill', 'tw-status__icon') +
      '<div class="tw-status__text"><span class="tw-status__title">' + esc(n.title) + '</span><span class="tw-status__body">' + esc(n.body) + '</span></div></div>';
  }
  function limitNotice(left, over, sent) {
    const body = sent > 0 ? t(C04, sent) : left > 0 ? t(LIMIT_N, left, over) : t(LIMIT_0);
    return { tone: 'caution', title: t(LIMIT_TITLE), body };
  }
  const netNotice = (sent) => ({ tone: 'error', title: sent > 0 ? t('已送出 {1} 項，其餘的沒有送出', sent) : t('沒有送出'), body: t('請檢查網路連線後再試一次。') });
  /* 雛形沒有後端，網址帶 fail 時才模擬「送到一半出事」：整批時第二張出事，只有一張時第一張就出事，讓 reviewer 看得到部分成功的畫面。 */
  function failAt(i, total) {
    if (FAIL !== 'midway' && FAIL !== 'net') return null;
    return i === (total > 1 ? 1 : 0) ? (FAIL === 'midway' ? 'limit' : 'net') : null;
  }
  let toastTimer = null;
  function toast(text, tone) {
    let el = $('#toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'toast';
      el.className = 'tw-toast';
      el.setAttribute('role', 'status');
      document.body.appendChild(el);
    }
    el.dataset.tone = tone;
    el.innerHTML = icon(tone === 'ok' ? 'checkmark_circle_fill' : 'exclamationmark_triangle_fill') + '<span class="tw-toast__text">' + esc(text) + '</span>';
    el.hidden = false;
    el.style.animation = 'none';
    void el.offsetWidth;
    el.style.animation = '';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 2600);
  }
  /* 字寬估算照原型：最長的一行超過 10em 時三個時段的問候一起改小，換時段時字級才不會跳。 */
  const emW = (x) => Array.from(String(x)).reduce((a, ch) => a + (ch === ' ' ? 0.3 : ch.charCodeAt(0) > 0x2E7F ? 1 : 0.58), 0);

  function renderHome() {
    document.title = pageTitle('');
    const tasks = sorted();
    const active = tasks.filter((x) => stage(x).active);
    const hour = Math.floor(S.clock / 60);
    const hi = hour < 11 ? '早安' : hour < 18 ? '午安' : '晚安';
    const small = Math.max(emW(t('早安')), emW(t('午安')), emW(t('晚安')), emW(t('今天需要什麼？'))) > 10;
    const cur = LANGS.find((l) => l[0] === lang);
    const menu = !view.menu ? '' : '<div class="tw-menu-scrim" data-act="menu-close"></div><div class="tw-menu" role="menu" aria-label="' + esc(t('語言')) + '"><p class="tw-menu__head">' + t('語言') + '</p>' +
      LANGS.map((l, i) => '<button class="tw-menu__item" id="lang-' + i + '" role="menuitemradio" aria-checked="' + (l[0] === lang) + '" lang="' + l[0] + '" data-act="pick-lang" data-v="' + l[0] + '"><span>' + l[1] + '</span>' + icon('checkmark', 'tw-menu__check') + '</button>').join('') + '</div>';
    let html = '<main class="tw-page tw-page--home">' +
      '<div class="tw-top"><span class="tw-top__pills"><span class="tw-pill">' + icon('bed_double') + ROOM + '</span>' +
      '<span class="tw-menu-wrap"><button class="tw-lang" id="lang" data-act="lang" aria-haspopup="menu" aria-expanded="' + view.menu + '" aria-label="' + esc(t('語言')) + '">' + icon('globe') + '<span>' + cur[2] + '</span>' + icon('chevron_down', 'tw-lang__chev') + '</button>' + menu + '</span></span></div>' +
      '<div class="g-hello"><span class="tw-label">' + esc(t('{1} 號房', ROOM)) + '</span><h1 class="tw-display' + (small ? ' tw-display--sm' : '') + '">' + t(hi) + '<em>' + t('今天需要什麼？') + '</em></h1></div>' +
      '<div class="tw-ask" aria-disabled="true">' + icon('chat_bubble_2') + '<span class="tw-ask__text">' + t('問問看：製冰機在哪裡？') + '</span><span class="tw-badge">' + t('即將開放') + '</span></div>';
    if (tasks.length) {
      html += '<div class="tw-section__head"><h2 class="tw-h2">' + t('我的需求') + '</h2><a class="tw-btn tw-btn--plain tw-btn--link" href="requests.html">' + t('全部 {1} 項', tasks.length) + '</a></div>';
      html += active.length ? progressCard(active[0]) :
        '<a class="tw-rcard tw-rcard--idle tw-rcard--link" href="requests.html"><span class="tw-rcard__text"><span class="tw-strong">' + t('目前沒有進行中的需求') + '</span><span class="tw-rcard__sub">' + t('查看紀錄') + '</span></span>' + icon('chevron_forward', 'tw-row__chev') + '</a>';
    }
    const hero = (c, href, a, em, ic, foot) => '<a class="tw-hcard" data-c="' + c + '" href="' + href + '"><span class="tw-hcard__title">' + t(a) + '<em>' + t(em) + '</em></span>' +
      '<span class="tw-illo" data-c="' + c + '" aria-hidden="true"><span class="tw-illo__token">' + icon(ic) + '</span></span><span class="tw-hcard__foot"><span>' + t(foot) + '</span>' + icon('chevron_forward') + '</span></a>';
    html += '<div class="tw-hcards">' + hero('peach', 'supply.html', '毛巾、礦泉水', '馬上送到房間', 'cube_box', '備品補充') + hero('teal', 'repair.html', '東西壞了？', C02, 'hammer', '設備報修') + '</div>';
    const soon = (c, ic, a, em) => '<div class="tw-rcard" aria-disabled="true"><span class="tw-illo" data-c="' + c + '" aria-hidden="true"><span class="tw-illo__token">' + icon(ic) + '</span></span>' +
      '<span class="tw-rcard__text"><span class="tw-rcard__title">' + t(a) + ' <em>' + t(em) + '</em></span><span class="tw-badge">' + t('即將開放') + '</span></span></div>';
    html += '<section class="tw-section" aria-labelledby="g-svc"><h2 class="tw-h2" id="g-svc">' + t('服務') + '</h2><div class="tw-rcards">' +
      soon('peach', 'clock', '晚點', '退房') + soon('sand', 'briefcase', '寄放', '行李') + soon('sky', 'car', '幫我', '叫車') + '</div></section>';
    const guide = infoPages().filter((p) => p.title);
    if (guide.length) {
      html += '<section class="tw-section" aria-labelledby="g-gd"><h2 class="tw-h2" id="g-gd">' + t('住宿指南') + '</h2><div class="tw-card tw-list">' +
        guide.map((p) => '<a class="tw-li" href="guide.html?key=' + encodeURIComponent(p.key) + '"><span class="tw-li__text"><span class="tw-li__name">' + esc(p.title) + '</span></span>' + icon('chevron_forward', 'tw-row__chev') + '</a>').join('') + '</div></section>';
    }
    html += '<p class="tw-section__note tw-section__note--center">' + t('緊急狀況請用房內電話撥 9 找櫃檯') + '</p></main>';
    app.innerHTML = html;
  }
  function progressCard(x) {
    const s = stage(x).step;
    const bars = STEPS.map((k, i) => '<span class="tw-progress__bar" data-on="' + (i <= s) + '"></span>').join('');
    const labels = STEPS.map((k, i) => '<span class="tw-progress__label" data-on="' + (i <= s) + '">' + t(k) + '</span>').join('');
    return '<a class="tw-pcard" href="request.html?id=' + encodeURIComponent(x.id) + '&amp;from=home"><span class="tw-pcard__head"><span class="tw-pcard__text"><span class="tw-pcard__eyebrow">' + t('進行中的需求') + '</span>' +
      '<span class="tw-pcard__title clamp2">' + esc(x.title) + '</span></span><span class="tw-pcard__icon">' + icon(TYPE_ICON[x.type]) + '</span></span>' +
      '<span class="g-bars"><span class="tw-progress" aria-hidden="true">' + bars + '</span><span class="tw-progress" aria-hidden="true">' + labels + '</span></span>' +
      '<span class="tw-pcard__row">' + icon('clock') + '<span>' + esc(cardLine(x)) + '</span><b>' + t('查看') + '</b>' + icon('chevron_forward', 'tw-row__chev') + '</span></a>';
  }

  function renderSupply() {
    document.title = pageTitle(t('備品補充'));
    const chosen = MENU.filter((m) => (view.qty[m.key] || 0) > 0);
    const label = chosen.length === 0 ? t('選擇數量') : chosen.length === 1 ? t('送出 1 項') : t('送出 {1} 項', chosen.length);
    const rows = MENU.map((m, i) => {
      const n = view.qty[m.key] || 0;
      return '<div class="tw-li"><span class="tw-icontile tw-icontile--sm" data-tone="warm">' + icon('cube_box') + '</span>' +
        '<span class="tw-li__text"><span class="tw-li__name">' + esc(m.label) + '</span><span class="tw-li__sub">' + t('最多 {1}', MAX) + '</span></span>' +
        '<span class="tw-stepper"><button class="tw-stepper__btn" id="dec-' + i + '" data-act="dec" data-i="' + i + '" aria-label="' + esc(t('減少{1}', m.label)) + '"' + (n <= 0 || view.busy ? ' disabled' : '') + '>' + icon('minus') + '</button>' +
        '<output class="tw-stepper__value" data-zero="' + (n === 0) + '" aria-live="polite">' + n + '</output>' +
        '<button class="tw-stepper__btn" id="inc-' + i + '" data-act="inc" data-i="' + i + '" aria-label="' + esc(t('增加{1}', m.label)) + '"' + (n >= MAX || view.busy ? ' disabled' : '') + '>' + icon('plus') + '</button></span></div>';
    }).join('');
    app.innerHTML = header(t('備品補充'), 'index.html') + '<main class="tw-page tw-page--sub tw-page--form">' +
      '<p class="tw-lead">' + t('選好數量一次送出，每一項會分別交給房務。') + '</p><div class="tw-card tw-list">' + rows + '</div>' + notice() + '<div class="g-fill"></div>' +
      '<div class="tw-actionbar"><div class="tw-actionbar__row"><button class="tw-btn tw-btn--filled tw-btn--lg" id="send" data-act="send-supply"' +
      (chosen.length && !view.busy ? '' : ' disabled') + (view.busy ? ' aria-busy="true"' : '') + '>' + label + '</button></div><p class="tw-actionbar__note">' + t(CANCEL_NOTE) + '</p></div></main>';
  }
  async function sendSupply() {
    const chosen = MENU.filter((m) => (view.qty[m.key] || 0) > 0);
    const left = Math.max(quotaLeft(), 0);
    if (chosen.length > left) {
      view.notice = limitNotice(left, chosen.length - left, 0);
      render();
      return;
    }
    view.busy = true;
    view.notice = null;
    render();
    const ids = [];
    for (let i = 0; i < chosen.length; i++) {
      await wait(500);
      const fail = failAt(i, chosen.length);
      if (fail) {
        view.busy = false;
        view.notice = fail === 'limit' ? limitNotice(0, 0, ids.length) : netNotice(ids.length);
        render();
        return;
      }
      const m = chosen[i];
      const n = view.qty[m.key];
      ids.push(create({ title: n > 1 ? m.label + ' ×' + n : m.label, type: 'amenity', state: 'pool', dept: 'housekeeping' }).id);
      view.qty[m.key] = 0;
    }
    location.href = 'sent.html?ids=' + ids.join(',');
  }

  const CHIPS = ['冷氣不冷', '電視無訊號', '燈具故障', '熱水不熱', '網路連不上', '其他'];
  const repairReady = () => !!view.chip && (view.chip !== '其他' || view.note.trim().length > 0);
  const charCount = (s) => Array.from(s).length;
  function renderRepair() {
    document.title = pageTitle(t('設備報修'));
    const other = view.chip === '其他';
    const chips = CHIPS.map((c, i) => '<button class="tw-chip" id="chip-' + i + '" role="radio" aria-checked="' + (view.chip === c) + '" data-act="chip" data-i="' + i + '"' + (view.busy ? ' disabled' : '') + '>' + t(c) + '</button>').join('');
    app.innerHTML = header(t('設備報修'), 'index.html') + '<main class="tw-page tw-page--sub tw-page--form">' +
      '<section class="tw-section"><h2 class="tw-h2" id="rp-what">' + t('哪裡有問題？') + '</h2><div class="tw-chips" role="radiogroup" aria-labelledby="rp-what">' + chips + '</div></section>' +
      '<label class="tw-field"><span class="tw-field__label">' + t(other ? '補充說明（必填）' : '補充說明（選填）') + '</span>' +
      '<textarea class="tw-field__input" id="note" maxlength="200" placeholder="' + esc(t('例如：浴室那盞一直閃')) + '"' + (view.busy ? ' disabled' : '') + '>' + esc(view.note) + '</textarea>' +
      '<span class="tw-field__foot"><span>' + t('可以用任何語言輸入') + '</span><span id="count">' + charCount(view.note) + ' / 200</span></span></label>' +
      notice() + '<div class="g-fill"></div>' +
      '<div class="tw-actionbar"><div class="tw-actionbar__row"><button class="tw-btn tw-btn--filled tw-btn--lg" id="send" data-act="send-repair"' +
      (repairReady() && !view.busy ? '' : ' disabled') + (view.busy ? ' aria-busy="true"' : '') + '>' + t('送出報修') + '</button></div></div></main>';
  }
  /* 類別一律用繁體中文寫進送出的文字，因為櫃檯與同仁讀的是中文；住客的介面語言另外以 lang 帶給後端。 */
  function repairText() {
    const note = view.note.trim();
    if (view.chip === '其他') return note;
    return note ? view.chip + '：' + note : view.chip;
  }
  async function sendRepair() {
    if (quotaLeft() <= 0) {
      view.notice = limitNotice(0, 0, 0);
      render();
      return;
    }
    view.busy = true;
    view.notice = null;
    render();
    await wait(600);
    const fail = failAt(0, 1);
    if (fail) {
      view.busy = false;
      view.notice = fail === 'limit' ? limitNotice(0, 0, 0) : netNotice(0);
      render();
      return;
    }
    const text = repairText();
    const routed = S.route === 'engineering';
    const x = create({ title: text.length <= 120 ? text : text.slice(0, 120) + '…', type: routed ? 'repair' : 'other', state: routed ? 'pool' : 'triage', dept: routed ? 'engineering' : 'frontdesk', lang: SEND_LANG[lang] });
    location.href = 'sent.html?ids=' + x.id;
  }

  function renderSent() {
    const ids = (Q.get('ids') || '').split(',').filter(Boolean);
    if (!ids.length) { location.replace('index.html'); return; }
    document.title = pageTitle('');
    const one = ids.length === 1;
    app.innerHTML = '<main class="tw-page tw-page--center"><div class="tw-empty tw-empty--success">' + icon('checkmark_circle_fill', 'tw-empty__icon') +
      '<h1 class="tw-empty__title">' + (one ? t('需求已送出') : t('已送出 {1} 項需求', ids.length)) + '</h1><p class="tw-empty__body">' + t('同仁接手後，進度會自動更新。') + '</p>' +
      '<div class="tw-empty__actions"><a class="tw-btn tw-btn--filled tw-btn--lg tw-btn--block" href="' + (one ? 'request.html?id=' + encodeURIComponent(ids[0]) + '&amp;from=sent' : 'requests.html') + '">' + t('查看進度') + '</a>' +
      '<a class="tw-btn tw-btn--plain" href="index.html">' + t('回首頁') + '</a></div></div></main>';
  }

  function renderRequests() {
    document.title = pageTitle(t('我的需求'));
    const tasks = sorted();
    const row = (x, ended) => '<a class="tw-li" href="request.html?id=' + encodeURIComponent(x.id) + '&amp;from=list"><span class="tw-icontile tw-icontile--sm"' + (ended ? ' data-tone="neutral"' : '') + '>' + icon(TYPE_ICON[x.type]) + '</span>' +
      '<span class="tw-li__text"><span class="tw-li__name clamp2">' + esc(x.title) + '</span><span class="tw-li__sub">' + esc(t('{1} 送出 · {2}', hm(x.at), deptOf(x))) + '</span></span>' + badge(x) + icon('chevron_forward', 'tw-row__chev') + '</a>';
    const section = (title, list, ended) => list.length ? '<section class="tw-section"><h2 class="tw-h2">' + t(title) + '</h2><div class="tw-card tw-list">' + list.map((x) => row(x, ended)).join('') + '</div></section>' : '';
    const body = tasks.length ? section('進行中', tasks.filter((x) => stage(x).active), false) + section('已結束', tasks.filter((x) => !stage(x).active), true) :
      '<div class="tw-empty">' + icon('list_bullet', 'tw-empty__icon') + '<h2 class="tw-empty__title">' + t('還沒有送出任何需求') + '</h2><p class="tw-empty__body">' + t(C05) + '</p></div>';
    app.innerHTML = header(t('我的需求'), 'index.html') + '<main class="tw-page tw-page--sub tw-page--list">' + body + '<p class="tw-section__note">' + t('狀態會自動更新。退房後這份清單會一併清除。') + '</p></main>';
  }

  const current = () => S.tasks.find((y) => y.id === Q.get('id'));
  function renderRequest() {
    const x = current();
    if (!x) { location.replace('requests.html'); return; }
    document.title = pageTitle(t('需求進度'));
    let html = header(t('需求進度'), Q.get('from') === 'home' ? 'index.html' : 'requests.html') + '<main class="tw-page tw-page--sub">' +
      '<div class="tw-card tw-card--row"><span class="tw-pcard__icon">' + icon(TYPE_ICON[x.type]) + '</span><div class="g-detail-text">' + badge(x) +
      '<h2 class="g-detail-title">' + esc(x.title) + '</h2><span class="g-detail-meta">' + esc(t('{1} 送出 · {2}', hm(x.at), deptOf(x))) + '</span></div></div>';
    const msg = statusMessage(x);
    if (msg) {
      html += '<div class="tw-status' + (msg.tone ? ' tw-status--' + msg.tone : '') + '">' + icon(msg.icon, 'tw-status__icon') +
        '<div class="tw-status__text"><span class="tw-status__title">' + esc(msg.title) + '</span><span class="tw-status__body">' + esc(msg.body) + '</span></div></div>';
    }
    if (showTrack(x)) {
      const s = stage(x).step;
      html += '<ol class="tw-track" aria-label="' + esc(t('需求進度')) + '">' + STEPS.map((k, i) => {
        const state = i < s || s === 3 ? 'done' : i === s ? 'current' : 'todo';
        const meta = stepMeta(x, i);
        return '<li class="tw-track__step" data-state="' + state + '"' + (state === 'current' ? ' aria-current="step"' : '') + '><span class="tw-track__rail"><span class="tw-track__dot">' + (state === 'done' ? icon('checkmark') : '') + '</span><span class="tw-track__line"></span></span>' +
          '<span class="tw-track__text"><span class="tw-track__title">' + t(k) + '</span>' + (meta ? '<span class="tw-track__meta">' + esc(meta) + '</span>' : '') + '</span></li>';
      }).join('') + '</ol>';
    }
    if (canRate(x)) {
      const n = view.stars;
      const stars = [1, 2, 3, 4, 5].map((k) => '<button class="tw-rating__star" id="star-' + k + '" role="radio" aria-checked="' + (k === n) + '" aria-label="' + esc(t('{1} 顆星', k)) + '" data-on="' + (k <= n) + '" data-act="star" data-k="' + k + '"' + (view.rating ? ' disabled' : '') + '>' + icon(k <= n ? 'star_fill' : 'star') + '</button>').join('');
      html += '<section class="tw-card g-rate"><h2 class="tw-h2">' + t('這次服務還滿意嗎？') + '</h2><div class="tw-rating"><div class="tw-rating__stars" role="radiogroup" aria-label="' + esc(t('為這次服務評分')) + '">' + stars + '</div>' +
        '<span class="tw-rating__label" aria-live="polite">' + t(n ? WORDS[n - 1] : '點星星評分') + '</span></div>' +
        '<button class="tw-btn tw-btn--filled tw-btn--block" id="rate" data-act="rate"' + (n && !view.rating ? '' : ' disabled') + (view.rating ? ' aria-busy="true"' : '') + '>' + t('送出評分') + '</button></section>';
    }
    if (canCancel(x)) {
      html += '<div class="g-bottom"><button class="tw-btn tw-btn--gray-danger tw-btn--block" id="cancel" data-act="cancel"' + (view.cancelling ? ' disabled aria-busy="true"' : '') + '>' + t('取消這個需求') + '</button>' +
        '<p class="tw-section__note tw-section__note--center">' + t(CANCEL_NOTE) + '</p></div>';
    } else if (isLocked(x)) {
      html += '<p class="tw-section__note tw-section__note--center">' + t(LOCKED) + '</p>';
    }
    html += '</main>';
    if (view.dlg) {
      html += '<div class="tw-dialog-scrim" data-act="dlg-close"><div class="tw-dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-t" aria-describedby="dlg-b" data-act="noop">' +
        '<h2 class="tw-dialog__title" id="dlg-t">' + t('取消這個需求？') + '</h2><p class="tw-dialog__body" id="dlg-b">' + esc(t('「{1}」會從處理清單移除。', x.title)) + '</p>' +
        '<div class="tw-dialog__actions"><button class="tw-btn tw-btn--gray" id="dlg-keep" data-act="dlg-close">' + t('返回|keep') + '</button><button class="tw-btn tw-btn--gray-danger" id="dlg-ok" data-act="dlg-ok">' + t('取消需求') + '</button></div></div></div>';
    }
    app.innerHTML = html;
  }
  async function doCancel() {
    view.dlg = false;
    view.cancelling = true;
    render();
    await wait(600);
    view.cancelling = false;
    const x = current();
    if (FAIL === 'claimed') {
      x.state = 'accepted';
      save();
      render();
      toast(t(TOO_LATE), 'warn');
      return;
    }
    if (FAIL === 'net') { render(); toast(t(NET_TOAST), 'warn'); return; }
    x.state = 'cancelled';
    save();
    render();
    toast(t('已取消需求'), 'ok');
  }
  async function doRate() {
    view.rating = true;
    render();
    await wait(600);
    view.rating = false;
    const x = current();
    if (FAIL === 'rated') {
      x.rating = 5;
      view.stars = 0;
      save();
      render();
      return;
    }
    if (FAIL === 'net') { render(); toast(t(NET_TOAST), 'warn'); return; }
    x.rating = view.stars;
    view.stars = 0;
    save();
    render();
    toast(t('謝謝您的評分'), 'ok');
  }

  function renderGuide() {
    const p = infoPages().find((y) => y.key === Q.get('key') && y.title);
    if (!p) { location.replace('index.html'); return; }
    document.title = pageTitle(p.title);
    app.innerHTML = header(p.title, 'index.html') + '<main class="tw-page tw-page--sub tw-page--guide"><p class="tw-body">' + esc(p.body || '') + '</p></main>';
  }

  function renderEntry() {
    if (view.entry === 'loading') {
      document.title = pageTitle('');
      app.innerHTML = '<div class="g-loading" role="status" aria-label="…"><span class="g-spinner"></span></div>';
      return;
    }
    if (view.entry === 'retry') {
      document.title = pageTitle('');
      app.innerHTML = '<main class="tw-page tw-page--center"><div class="tw-empty">' + icon('wifi', 'tw-empty__icon') + '<h1 class="tw-empty__title">' + t('連線失敗，請再試一次') + '</h1>' +
        '<div class="tw-empty__actions"><button class="tw-btn tw-btn--filled tw-btn--lg tw-btn--block" id="retry" data-act="retry"' + (view.busy ? ' disabled aria-busy="true"' : '') + '>' + t('重試') + '</button></div></div></main>';
      return;
    }
    document.title = t('請洽櫃檯');
    app.innerHTML = '<main class="tw-page tw-page--center"><div class="tw-empty tw-empty--caution">' + icon('info_circle_fill', 'tw-empty__icon') + '<h1 class="tw-empty__title">' + t('請洽櫃檯') + '</h1>' +
      '<p class="tw-empty__body">' + t(C01) + '</p><div class="tw-empty__actions"><div class="tw-info"><span class="tw-info__icon">' + icon('phone') + '</span>' +
      '<div class="tw-info__text"><span class="tw-info__label">' + t('櫃檯（24 小時）') + '</span><span class="tw-info__value">' + t('房內電話撥 9') + '</span></div></div></div></div></main>';
  }
  async function retry() {
    view.busy = true;
    render();
    await wait(900);
    view.busy = false;
    if (Q.get('then') === 'unavailable') { view.entry = 'unavailable'; render(); return; }
    location.href = 'index.html';
  }

  const PAGES = { home: renderHome, supply: renderSupply, repair: renderRepair, sent: renderSent, requests: renderRequests, request: renderRequest, guide: renderGuide, entry: renderEntry };
  function render() {
    const focusId = document.activeElement && document.activeElement.id;
    document.documentElement.lang = lang;
    document.documentElement.dataset.theme = S.theme;
    (PAGES[PAGE] || renderHome)();
    const again = focusId && document.getElementById(focusId);
    if (again && again !== document.activeElement && !again.disabled) again.focus({ preventScroll: true });
    const hdr = $('#hdr');
    if (hdr) hdr.dataset.scrolled = String(window.scrollY > 4);
  }
  const focus = (id) => { const el = document.getElementById(id); if (el) el.focus(); };

  app.addEventListener('click', (e) => {
    const back = e.target.closest('#back');
    if (back) {
      /* 從返回目的地過來時用瀏覽器的上一頁，首頁的捲動位置才會跟著回來；其他情況（例如從成功頁進來）照連結走。 */
      let ref = '';
      try { ref = document.referrer ? new URL(document.referrer).pathname.split('/').pop() || 'index.html' : ''; } catch (err) { ref = ''; }
      if (ref && ref === back.getAttribute('href') && history.length > 1) { e.preventDefault(); history.back(); }
      return;
    }
    const el = e.target.closest('[data-act]');
    if (!el) return;
    const act = el.dataset.act;
    const i = Number(el.dataset.i);
    if (act === 'lang') { view.menu = !view.menu; render(); if (view.menu) focus('lang-' + LANGS.findIndex((l) => l[0] === lang)); }
    else if (act === 'menu-close') { view.menu = false; render(); focus('lang'); }
    else if (act === 'pick-lang') { lang = el.dataset.v; ls.set(LANG_KEY, lang); view.menu = false; render(); focus('lang'); }
    else if (act === 'dec' || act === 'inc') {
      const k = MENU[i].key;
      view.qty[k] = Math.min(MAX, Math.max(0, (view.qty[k] || 0) + (act === 'inc' ? 1 : -1)));
      view.notice = null;
      render();
    }
    else if (act === 'send-supply') sendSupply();
    else if (act === 'chip') { view.chip = CHIPS[i]; view.notice = null; render(); }
    else if (act === 'send-repair') sendRepair();
    else if (act === 'cancel') { view.dlg = true; render(); focus('dlg-keep'); }
    else if (act === 'dlg-close') { view.dlg = false; render(); focus('cancel'); }
    else if (act === 'dlg-ok') doCancel();
    else if (act === 'star') { view.stars = Number(el.dataset.k); render(); }
    else if (act === 'rate') doRate();
    else if (act === 'retry') retry();
  });
  app.addEventListener('input', (e) => {
    if (e.target.id !== 'note') return;
    view.note = e.target.value;
    $('#count').textContent = charCount(view.note) + ' / 200';
    $('#send').disabled = !repairReady();
    if (view.notice) { view.notice = null; const box = $('.tw-status'); if (box) box.remove(); }
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && view.dlg) { view.dlg = false; render(); focus('cancel'); }
    else if (e.key === 'Escape' && view.menu) { view.menu = false; render(); focus('lang'); }
    else if (e.key === 'Tab' && view.dlg) {
      const first = $('#dlg-keep');
      const last = $('#dlg-ok');
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  });
  window.addEventListener('scroll', () => { const hdr = $('#hdr'); if (hdr) hdr.dataset.scrolled = String(window.scrollY > 4); }, { passive: true });
  /* 用瀏覽器的上一頁回來時頁面可能是快取的舊畫面，例如剛取消的需求還在「進行中」，所以重讀資料再畫一次。 */
  window.addEventListener('pageshow', (e) => { if (e.persisted) { Object.assign(S, ss.get(KEY, S)); render(); } });

  /* 雛形沒有後端的即時訊號；網址帶 live 時用計時器代替同仁或櫃檯的動作，讓停在首頁、清單、進度頁的人看得到畫面自己變。 */
  const NEXT = { triage: 'pool', pool: 'accepted', accepted: 'doing', deferred: 'doing', doing: 'done', interrupted: 'done' };
  function tick() {
    Object.assign(S, ss.get(KEY, S));
    if (S.live === 'answer') {
      const y = sorted().find((z) => z.state === 'triage');
      if (!y) return;
      y.answer = '已請工務上去看';
      y.state = 'done';
    } else {
      const open = PAGE === 'request' ? current() : null;
      const y = open && stage(open).active ? open : sorted().find((z) => stage(z).active);
      if (!y) return;
      if (y.state === 'triage') y.dept = /冷氣|電視|燈|熱水|網路/.test(y.title) ? 'engineering' : 'housekeeping';
      y.state = NEXT[y.state];
    }
    save();
    if (PAGE === 'home' || PAGE === 'requests' || PAGE === 'request') render();
  }
  if (S.live === '1' || S.live === 'answer') setInterval(tick, 4000);

  render();
})();
