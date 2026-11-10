/* 已處理的單記在這個瀏覽器分頁裡，關掉分頁即還原 */
const Done = {
  key: 'managerProcessed',
  all() { try { return JSON.parse(sessionStorage.getItem(this.key) || '[]'); } catch (e) { return []; } },
  has(id) { return this.all().includes(id); },
  add(id) { const a = this.all(); if (!a.includes(id)) { a.push(id); sessionStorage.setItem(this.key, JSON.stringify(a)); } },
};
const Q = new URLSearchParams(location.search);
const TODAY = '11/10';
function whenParts(dateStr, time) {
  return dateStr === TODAY ? { date: '', time } : { date: dateStr, time };
}
/* 示範資料：三類待辦 */
const QUEUE = {
  inspection: [
    { id: '303', room: '303', title: '退房打掃', cleaner: '小林', date: '11/09', time: '17:40', redo: 0 },
    { id: '301', room: '301', title: '退房打掃', cleaner: '小美', date: TODAY, time: '10:20', redo: 0 },
  ],
  review: [
    { id: 'W-0201', no: 'W-0201', room: '202', title: '窗簾掛勾鬆脫', category: '家具設備', reporter: '阿芳', photos: 1, date: TODAY, time: '09:12' },
  ],
  verify: [
    { id: 'W-0110', no: 'W-0110', room: '208', title: '水龍頭滴水', result: '暫時處理', fixer: '阿強', date: '11/09', time: '16:10' },
    { id: 'W-0108', no: 'W-0108', room: '301', title: '門鎖電池更換', result: '已修好', fixer: '阿強', date: TODAY, time: '09:48' },
    { id: 'W-0111', no: 'W-0111', room: '5 樓製冰機', title: '定期保養', result: '已修好', fixer: '阿強', scheduled: true, date: TODAY, time: '11:05' },
  ],
};
function remaining(kind) { return QUEUE[kind].filter(r => !Done.has(r.id)); }
function showDialog(id) { document.getElementById(id).classList.add('show'); }
function hideDialog(id) { document.getElementById(id).classList.remove('show'); }
function isOffline() { return Q.get('offline') === '1'; }
function carryOffline(url) { return isOffline() ? url + (url.includes('?') ? '&' : '?') + 'offline=1' : url; }
