/* 示範資料與這個瀏覽器分頁裡的狀態；關掉分頁即還原 */
const Q = new URLSearchParams(location.search);
const TODAY = '11/11';
const YESTERDAY = '11/10';

function ssGet(key, fallback) { try { const v = sessionStorage.getItem(key); return v === null ? fallback : JSON.parse(v); } catch (e) { return fallback; } }
function ssSet(key, value) { try { sessionStorage.setItem(key, JSON.stringify(value)); } catch (e) {} }

/* 帳號 */
const ACCOUNTS = {
  mg1: { id: 'mg1', name: '王主管', role: 'manager', kind: 'housekeeping', title: '房務主管' },
  mg2: { id: 'mg2', name: '陳主管', role: 'manager', kind: 'engineering', title: '工務主管' },
  mt1: { id: 'mt1', name: '阿強', role: 'staff', dept: 'engineering', title: '工務' },
  hk2: { id: 'hk2', name: '阿芳', role: 'staff', dept: 'housekeeping', title: '房務' },
};
(function pickManager() {
  const as = Q.get('as');
  if (as === 'mg1' || as === 'mg2') ssSet('managerAccount', as);
})();
function me() { return ACCOUNTS[ssGet('managerAccount', 'mg1')] || ACCOUNTS.mg1; }
function isEngineeringManager() { return me().kind === 'engineering'; }

/* 已處理的待查房 */
const Done = {
  all() { return ssGet('managerProcessed', []); },
  has(id) { return this.all().includes(id); },
  add(id) { const a = this.all(); if (!a.includes(id)) { a.push(id); ssSet('managerProcessed', a); } },
};

const INSPECTION_QUEUE = [
  { id: '303', room: '303', title: '退房打掃', cleaner: '小林', date: YESTERDAY, time: '17:40', redo: 0 },
  { id: '301', room: '301', title: '退房打掃', cleaner: '小美', date: TODAY, time: '10:20', redo: 0 },
];

/* 工務單：state 為 pool（等工務接單）、accepted（待開始）、doing（維修中）、verify（已完修等驗收）、closed（已結案） */
const BASE_TICKETS = {
  'W-0108': { room: '301', title: '門鎖電池更換', category: '門鎖', desc: '門鎖低電量警示', reporter: '阿芳', created: '11/10 15:30', photos: 1,
    state: 'verify', assignee: '阿強', onShift: true, result: '已修好', note: '更換四顆三號電池，刷卡測試三次正常', doneDate: TODAY, doneTime: '09:48', completionPhotos: 1 },
  'W-0110': { room: '208', title: '水龍頭滴水', category: '水電', desc: '浴室洗手台水龍頭關不緊，一直滴', reporter: '小美', created: '11/10 10:05', photos: 2,
    state: 'verify', assignee: '阿強', onShift: true, result: '暫時處理', note: '先鎖緊止水閥，已另開追蹤單換墊片', doneDate: YESTERDAY, doneTime: '16:10', completionPhotos: 1, conflictOnAction: true },
  'W-0111': { room: '5 樓製冰機', publicArea: true, title: '定期保養', category: '其他', desc: '每月定期保養：清洗濾網、檢查製冰量', reporter: null, created: '11/11 08:00', photos: 0, scheduled: true,
    state: 'verify', assignee: '阿德', onShift: false, result: '已修好', note: '濾網已清洗，製冰量正常', doneDate: TODAY, doneTime: '11:05', completionPhotos: 1 },
  'W-0109': { room: '405', title: '電視遙控器失靈', category: '其他', desc: '遙控器沒反應', reporter: '阿芳', created: '11/09 14:10', photos: 1,
    state: 'closed', assignee: '阿強', result: '已修好', note: '更換遙控器', doneDate: '11/09', doneTime: '16:20', completionPhotos: 1, verifiedBy: '王主管', verifiedAt: '11/09 17:02' },
  'W-0114': { room: '305', title: '浴室燈不亮', category: '水電', desc: '浴室燈按了沒反應', reporter: '阿芳', created: '11/11 08:40', photos: 1,
    state: 'closed', assignee: '阿強', result: '不需處理', note: '燈正常，開關在門邊', doneDate: TODAY, doneTime: '09:05', completionPhotos: 0 },
  'W-0117': { room: '208', title: '浴室排水慢', category: '水電', desc: '淋浴間排水很慢，會積水', reporter: '小美', created: '11/10 13:20', photos: 1,
    state: 'accepted', assignee: '阿強', onShift: true,
    lastFail: { by: '王主管', reason: '排水仍慢，積水約 2 公分', at: '今天 10:05' },
    prev: { result: '已修好', note: '清除排水孔毛髮', completionPhotos: 1, doneAt: '今天 09:20' } },
  'W-0202': { room: '306', title: '冷氣不冷', category: '冷氣', desc: '冷氣開到 18 度還是不冷，住客抱怨', reporter: '阿芳', created: '11/11 09:40', photos: 1, urgent: true, state: 'pool' },
  'W-0112': { room: '210', title: '燈管閃爍', category: '水電', desc: '浴室燈管一直閃', reporter: '小林', created: '11/11 08:50', photos: 1, state: 'doing', assignee: '阿強' },
  'W-0115': { room: '210', title: '冷氣濾網更換', category: '冷氣', desc: '冷氣出風有異味，濾網該換了', reporter: '陳主管', created: '11/11 09:10', photos: 0, state: 'doing', assignee: '陳主管', onShift: true },
  'W-0116': { room: '407', title: '冰箱不冷', category: '家電', desc: '房內小冰箱不冷，飲料是溫的', reporter: '小美', created: '11/11 09:55', photos: 1, state: 'doing', assignee: '陳主管', onShift: true },
};
function ticket(no) {
  const base = BASE_TICKETS[no];
  if (!base) return null;
  return Object.assign({ no }, base, ssGet('ticketPatch', {})[no] || {});
}
function patchTicket(no, patch) { const all = ssGet('ticketPatch', {}); all[no] = Object.assign(all[no] || {}, patch); ssSet('ticketPatch', all); }
function allTickets() { return Object.keys(BASE_TICKETS).map(ticket); }
function verifyQueue() {
  return allTickets().filter(t => t.state === 'verify')
    .sort((a, b) => (a.doneDate + a.doneTime).localeCompare(b.doneDate + b.doneTime));
}
function inspectionQueue() { return INSPECTION_QUEUE.filter(r => !Done.has(r.id)); }
function nowHM() { return '14:20'; }

/* 共用 */
function showDialog(id) { document.getElementById(id).classList.add('show'); }
function hideDialog(id) { document.getElementById(id).classList.remove('show'); }
function isOffline() { return Q.get('offline') === '1'; }
function carryOffline(url) { return isOffline() ? url + (url.includes('?') ? '&' : '?') + 'offline=1' : url; }
function go(url) { location.href = carryOffline(url); }
function applyOffline() { if (isOffline()) document.getElementById('phone').classList.add('offline'); }

/* 底部「我的」分頁：看帳號、登出、重新登入 */
function openMe() {
  const phone = document.getElementById('phone');
  let layer = document.getElementById('meSheet');
  if (!layer) {
    layer = document.createElement('div');
    layer.className = 'sheetscrim';
    layer.id = 'meSheet';
    phone.appendChild(layer);
  }
  const m = me();
  layer.innerHTML = `<div class="sheet">
      <div class="grab"></div>
      <div class="t">${m.name}</div>
      <div class="s">${m.title} · 員工編號 ${m.id}</div>
      <div class="actions" style="margin-top:20px"><button class="pill outlined" onclick="hideDialog('meSheet')">關閉</button><button class="pill" onclick="openLogin()">登出</button></div>
    </div>`;
  showDialog('meSheet');
}
function openLogin() {
  const phone = document.getElementById('phone');
  let layer = document.getElementById('loginLayer');
  if (!layer) {
    layer = document.createElement('div');
    layer.id = 'loginLayer';
    layer.className = 'loginlayer';
    phone.appendChild(layer);
  }
  layer.innerHTML = `<div class="loginbox">
      <div class="brandmark">TMS</div>
      <div class="t">登入</div>
      <label>員工編號<input id="loginId" autocomplete="off" value="${me().id === 'mg1' ? 'mg2' : 'mg1'}"></label>
      <label>密碼<input id="loginPw" type="password" value="demo1234"></label>
      <div class="loginerr" id="loginErr"></div>
      <button class="pill" onclick="doLogin()">登入</button>
    </div>`;
  layer.classList.add('show');
}
function doLogin() {
  const id = document.getElementById('loginId').value.trim();
  if (id !== 'mg1' && id !== 'mg2') { document.getElementById('loginErr').textContent = '員工編號或密碼不正確'; return; }
  ssSet('managerAccount', id);
  location.href = 'index.html';
}
