/**
 * TMS POC — mock 任務引擎
 *
 * 設計重點（見 /Users/Caresys/.claude/plans/drifting-bubbling-candy.md）：
 * - 事件為真相：所有寫入先 append 到 state.events，再從事件推出 state.tasks 投影
 *   （本實作簡化為「直接改投影 + 同步寫一筆事件」，效果等價，方便 POC 閱讀）。
 * - 九態狀態機（以 docs/11-requirements-architecture.html 為準，全名不縮寫）：
 *     triage / pool / accepted / doing / done / inspected / deferred / interrupted / cancelled
 * - 跨分頁同步：localStorage('tms_poc_state') + BroadcastChannel('tms_poc')，
 *   訊息只帶 {type, rev}，收到後重讀 localStorage 再重繪；另掛 storage 事件做降級。
 * - 搶單：CAS 版本比對，版本不符回 {ok:false, reason:'already_taken'}。
 * - 查房：逐項判定，任一不過整間退回原清房者、redo+1；清房者=查房者時擋下 403。
 *
 * 不做（demo 口頭說明即可）：SLA 自動升級、429 限流、真 SSE（用 BroadcastChannel 等價替代）。
 */
(function (global) {
  'use strict';

  var STORAGE_KEY = 'tms_poc_state';
  var CHANNEL_NAME = 'tms_poc';

  var STATES = [
    'triage', 'pool', 'accepted', 'doing', 'done',
    'inspected', 'deferred', 'interrupted', 'cancelled'
  ];

  var STATE_LABELS = {
    triage: '待判讀',
    pool: '待接池',
    accepted: '已指派',
    doing: '進行中',
    done: '已完成',
    inspected: '已查驗',
    deferred: '延後',
    interrupted: '中斷',
    cancelled: '已取消'
  };

  var DEPT_LABELS = {
    housekeeping: '房務',
    maintenance: '工務',
    desk: '櫃檯'
  };

  var TYPE_LABELS = {
    cleaning: '打掃',
    amenity: '備品',
    repair: '修繕',
    other: '其他'
  };

  // 查房與房務 SOP 共用同一套逐項清單，兩邊才能互相對得上（僅 type=cleaning 適用）。
  var INSPECTION_ITEMS = [
    { key: 'bed', label: '床鋪' },
    { key: 'bathroom', label: '浴室' },
    { key: 'amenity', label: '備品' },
    { key: 'qr', label: 'QR 貼紙' }
  ];

  // BroadcastChannel 在部分瀏覽器隱私模式下會建構失敗，吞掉例外退化成只靠 storage 事件。
  var channel = null;
  try {
    if ('BroadcastChannel' in global) {
      channel = new BroadcastChannel(CHANNEL_NAME);
    }
  } catch (e) {
    channel = null;
  }

  var listeners = [];

  function now() {
    return Date.now();
  }

  function uid(prefix) {
    return prefix + '_' + Math.random().toString(36).slice(2, 9) + Date.now().toString(36).slice(-4);
  }

  function clone(obj) {
    return JSON.parse(JSON.stringify(obj));
  }

  function readRaw() {
    try {
      var raw = global.localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      console.error('[tms-engine] 讀取 localStorage 失敗', e);
      return null;
    }
  }

  function writeRaw(state) {
    try {
      global.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      console.error('[tms-engine] 寫入 localStorage 失敗', e);
    }
  }

  // 無狀態時自動 seed，讓任何一頁都可能是「第一個開啟的分頁」而不用另外初始化流程。
  // 瀏覽器裡可能留著「還沒有 settings 欄位」的舊狀態，讀到就用出廠預設補齊，
  // 不逼使用者重置。
  function getState() {
    var s = readRaw();
    if (!s) {
      s = global.TMS_SEED.buildSeedState();
      writeRaw(s);
    }
    if (!s.settings) {
      s.settings = global.TMS_SEED.defaultSettings();
      writeRaw(s);
    } else {
      var defaults = global.TMS_SEED.defaultSettings();
      var patched = false;
      Object.keys(defaults).forEach(function (k) {
        if (!(k in s.settings)) { s.settings[k] = defaults[k]; patched = true; }
      });
      if (patched) writeRaw(s);
    }
    if (!s.schedule) {
      s.schedule = { draft: [], source: null, importedAt: null };
      writeRaw(s);
    }
    return s;
  }

  function save(state) {
    state.rev = (state.rev || 0) + 1;
    state.updatedAt = now();
    writeRaw(state);
    broadcast(state.rev);
    notifyLocal();
  }

  function broadcast(rev) {
    if (channel) {
      try {
        channel.postMessage({ type: 'state_changed', rev: rev });
      } catch (e) { /* 傳送失敗不影響本地已落盤的狀態，忽略即可 */ }
    }
  }

  function notifyLocal() {
    listeners.forEach(function (cb) {
      try { cb(); } catch (e) { console.error('[tms-engine] listener 錯誤', e); }
    });
  }

  function subscribe(cb) {
    listeners.push(cb);
    return function unsubscribe() {
      listeners = listeners.filter(function (f) { return f !== cb; });
    };
  }

  // 同機瀏覽器分頁的 BroadcastChannel 幾乎是零延遲，快到兩個人「同時搶單」的視窗
  // 窄到人手點擊速度完全碰不到——這點延遲只加在「收到別人訊息」這端，用來還原
  // 真實後端會有的網路往返時間，讓搶單衝突這個示範場景點得到；本地自己寫入仍然
  // 立刻重繪，不影響「住客送出 2 秒內上看板」的 NFR（這裡遠遠小於 2 秒）。
  var REMOTE_SYNC_DELAY_MS = 400;
  function notifyLocalDelayed() {
    setTimeout(notifyLocal, REMOTE_SYNC_DELAY_MS);
  }
  if (channel) {
    channel.onmessage = function () { notifyLocalDelayed(); };
  }
  global.addEventListener('storage', function (ev) {
    if (ev.key === STORAGE_KEY) { notifyLocalDelayed(); }
  });

  function addEvent(state, taskId, type, actorName, payload) {
    state.events.push({
      id: uid('ev'),
      taskId: taskId,
      type: type,
      actor: actorName || '系統',
      ts: now(),
      payload: payload || {}
    });
  }

  function findTask(state, taskId) {
    for (var i = 0; i < state.tasks.length; i++) {
      if (state.tasks[i].id === taskId) return state.tasks[i];
    }
    return null;
  }

  function resolveStaffName(state, staffId) {
    var s = (state.staff || []).filter(function (x) { return x.id === staffId; })[0];
    return s ? s.name : staffId;
  }

  // state -> { action -> nextState|null }；nextState 為 null 代表「允許但不換狀態」
  // （SOP 勾選、評分）。沒列在表裡的組合一律視為非法轉移，對應規格書講的
  // 「條件式 UPDATE 衝突 409」在 POC 裡的等價行為。
  var TRANSITIONS = {
    triage: { triage_classify: 'pool', triage_answer: 'done', guest_withdraw: 'cancelled' },
    pool: { claim: 'accepted', guest_withdraw: 'cancelled' },
    accepted: { start: 'doing', defer: 'deferred' },
    doing: { sop_toggle: null, complete: 'done', interrupt: 'interrupted' },
    done: { inspect_pass: 'inspected', inspect_fail: 'accepted', guest_rate: null },
    inspected: { guest_rate: null },
    deferred: { reactivate: 'accepted' },
    interrupted: { resume: 'doing' },
    cancelled: {}
  };

  function canTransition(state, action) {
    return !!(TRANSITIONS[state] && (action in TRANSITIONS[state]));
  }

  // 所有「會改狀態」的動作最終都走這裡，是整個引擎唯一真正動 task.state 的地方，
  // 這樣白名單表格才有意義——繞過這個函式去直接改 state.tasks 就會破壞保證。
  function transition(taskId, action, actorName, mutate) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) {
      return { ok: false, code: 'not_found', message: '找不到這筆任務（可能已被重置）' };
    }
    if (!canTransition(task.state, action)) {
      return {
        ok: false,
        code: 'invalid_transition',
        message: '非法狀態轉移：「' + STATE_LABELS[task.state] + '」不能執行「' + action + '」'
      };
    }
    var nextState = TRANSITIONS[task.state][action];
    var fromState = task.state;
    // mutate() 可以回傳一段要併進事件 payload 的資訊（例如評了幾星），
    // 沒有回傳值的話就只記 from/to，對「不換狀態」的動作（如評分）還是看得出發生過什麼。
    var extraPayload = mutate ? mutate(task, state) : null;
    if (nextState) task.state = nextState;
    task.version = (task.version || 1) + 1;
    task.updatedAt = now();
    var payload = { from: fromState, to: task.state };
    if (extraPayload) { for (var k in extraPayload) { payload[k] = extraPayload[k]; } }
    addEvent(state, taskId, action, actorName, payload);
    save(state);
    return { ok: true, task: clone(task) };
  }

  // 住客勾選式需求直接命中部門池，模擬規則引擎分類；自由文字一律先進 triage 給櫃檯判讀。
  var CHECKLIST_RULES = {
    towel: { dept: 'housekeeping', type: 'amenity', title: '加毛巾', ruleHit: '毛巾 → 房務待接池' },
    extra_bed: { dept: 'housekeeping', type: 'amenity', title: '加床', ruleHit: '加床 → 房務待接池' },
    toiletries: { dept: 'housekeeping', type: 'amenity', title: '補備品（牙刷/刮鬍刀）', ruleHit: '備品 → 房務待接池' },
    ac_repair: { dept: 'maintenance', type: 'repair', title: '冷氣故障', ruleHit: '冷氣故障 → 工務待接池' },
    plumbing: { dept: 'maintenance', type: 'repair', title: '水電問題', ruleHit: '水電問題 → 工務待接池' }
  };

  function guestSubmitChecklist(roomNo, ruleKey) {
    var state = getState();
    var rule = menuRule(state, ruleKey);
    if (!rule) return { ok: false, code: 'unknown_rule', message: '未知的需求類型' };
    var task = {
      id: uid('task'),
      roomNo: roomNo,
      dept: rule.dept,
      type: rule.type,
      state: 'pool',
      assigneeId: null,
      title: rule.title,
      detail: '',
      source: 'guest_checklist',
      ruleHit: rule.ruleHit,
      redoCount: 0,
      sop: rule.type === 'cleaning' ? seedSop(state) : [],
      rating: null,
      version: 1,
      createdAt: now(),
      updatedAt: now()
    };
    state.tasks.push(task);
    addEvent(state, task.id, 'created', '住客 ' + roomNo, { source: 'guest_checklist', ruleHit: rule.ruleHit });
    save(state);
    return { ok: true, task: clone(task) };
  }

  function guestSubmitFreetext(roomNo, text) {
    if (!text || !text.trim()) {
      return { ok: false, code: 'empty', message: '請輸入內容再送出' };
    }
    var state = getState();
    // 流程開關：預設自由文字進 triage 給櫃檯判讀；關掉判讀就直接進房務待接池。
    var needsTriage = !state.settings.flow || state.settings.flow.freetextNeedsTriage !== false;
    var task = {
      id: uid('task'),
      roomNo: roomNo,
      dept: needsTriage ? null : 'housekeeping',
      type: 'other',
      state: needsTriage ? 'triage' : 'pool',
      assigneeId: null,
      title: text.trim().slice(0, 24),
      detail: text.trim(),
      source: 'guest_freetext',
      ruleHit: needsTriage ? null : '流程設定：自由文字直接進房務待接池',
      redoCount: 0,
      sop: [],
      rating: null,
      version: 1,
      createdAt: now(),
      updatedAt: now()
    };
    state.tasks.push(task);
    addEvent(state, task.id, 'created', '住客 ' + roomNo, { source: 'guest_freetext' });
    save(state);
    return { ok: true, task: clone(task) };
  }

  // 收回限定 triage/pool：一旦進了 accepted 之後代表已有人力配置在上面，
  // 住客端片面取消會跟現場對不上，所以規格要求轉而「請洽櫃檯」走人工協調。
  function guestWithdraw(taskId) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) return { ok: false, code: 'not_found', message: '找不到這筆任務' };
    if (task.state !== 'triage' && task.state !== 'pool') {
      return { ok: false, code: 'cannot_withdraw', message: '已進入處理流程，請洽櫃檯' };
    }
    return transition(taskId, 'guest_withdraw', '住客 ' + task.roomNo, function (t) {
      t.withdrawReason = 'guest_withdrew';
    });
  }

  function guestRate(taskId, stars) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) return { ok: false, code: 'not_found', message: '找不到這筆任務' };
    if (task.state !== 'done' && task.state !== 'inspected') {
      return { ok: false, code: 'invalid_transition', message: '任務尚未完成，無法評分' };
    }
    return transition(taskId, 'guest_rate', '住客 ' + task.roomNo, function (t) {
      t.rating = stars;
      return { stars: stars };
    });
  }

  function deskTriage(taskId, dept, actorName) {
    return transition(taskId, 'triage_classify', actorName || '櫃檯', function (t) {
      t.dept = dept;
      if (dept === 'housekeeping' && t.type === 'other') t.type = 'amenity';
      if (dept === 'maintenance' && t.type === 'other') t.type = 'repair';
    });
  }

  function deskAnswer(taskId, answerText, actorName) {
    return transition(taskId, 'triage_answer', actorName || '櫃檯', function (t) {
      t.detail = (t.detail ? t.detail + '\n' : '') + '【櫃檯回覆】' + answerText;
    });
  }

  function sendNotice(message, actorName) {
    var state = getState();
    state.notices = state.notices || [];
    var notice = { id: uid('ntc'), message: message, actor: actorName || '櫃檯', ts: now(), readBy: [] };
    state.notices.unshift(notice);
    addEvent(state, notice.id, 'notice_sent', actorName || '櫃檯', { message: message });
    save(state);
    return { ok: true, notice: notice };
  }

  function readNotice(noticeId, staffName) {
    var state = getState();
    var n = (state.notices || []).filter(function (x) { return x.id === noticeId; })[0];
    if (!n) return { ok: false, code: 'not_found', message: '找不到這則通知' };
    if (n.readBy.indexOf(staffName) === -1) n.readBy.push(staffName);
    addEvent(state, noticeId, 'notice_read', staffName, {});
    save(state);
    return { ok: true, notice: n };
  }

  function logLostFound(roomNo, item, actorName, photo) {
    var state = getState();
    state.lostFound = state.lostFound || [];
    // 拾獲人要記在案：實務上多半是房務打掃時發現、櫃檯保管歸還，追責與對帳都要知道是誰收的。
    // 照片只掛在紀錄上，事件 payload 只記 hasPhoto——base64 進流水帳會把日誌撐爆。
    var rec = { id: uid('lf'), roomNo: roomNo, item: item, status: 'held', foundBy: actorName || '櫃檯', photo: photo || null, ts: now() };
    state.lostFound.unshift(rec);
    addEvent(state, rec.id, 'lost_found_logged', actorName || '櫃檯', { roomNo: roomNo, item: item, hasPhoto: !!photo });
    save(state);
    return { ok: true, record: rec };
  }

  function returnLostFound(recordId, actorName) {
    var state = getState();
    var rec = (state.lostFound || []).filter(function (x) { return x.id === recordId; })[0];
    if (!rec) return { ok: false, code: 'not_found', message: '找不到這筆遺失物' };
    rec.status = 'returned';
    addEvent(state, recordId, 'lost_found_returned', actorName || '櫃檯', {});
    save(state);
    return { ok: true, record: rec };
  }

  function seedSop(state) {
    var items = ((state || getState()).settings.inspectionItems) || INSPECTION_ITEMS;
    return items.map(function (i) { return { key: i.key, label: i.label, checked: false }; });
  }

  // 自建選單項與內建規則共用同一個解析入口：住客勾選送出時只拿著 key，
  // 得在這裡決定它進哪個部門池。
  function menuRule(state, key) {
    if (CHECKLIST_RULES[key]) return CHECKLIST_RULES[key];
    var c = (state.settings.customMenuItems || []).filter(function (x) { return x.key === key; })[0];
    if (!c) return null;
    return {
      dept: c.dept,
      type: c.type,
      title: c.title,
      ruleHit: c.title + ' → ' + (DEPT_LABELS[c.dept] || c.dept) + '待接池（自建項目）'
    };
  }

  // 搶單＝CAS：先判「還在不在池子裡」，再判「版本跟 UI 當初讀到的是不是同一份」，
  // 兩層都過才算搶到，否則一律回 already_taken，不區分細節原因（UI 只需要顯示搶輸）。
  function claimTask(taskId, staffId, staffName, expectedVersion) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) return { ok: false, code: 'not_found', message: '找不到這筆任務' };
    if (task.state !== 'pool') {
      return {
        ok: false,
        code: 'already_taken',
        message: '已被「' + (resolveStaffName(state, task.assigneeId) || '其他人') + '」搶走'
      };
    }
    if (typeof expectedVersion === 'number' && expectedVersion !== task.version) {
      return {
        ok: false,
        code: 'already_taken',
        message: '已被「' + (resolveStaffName(state, task.assigneeId) || '其他人') + '」搶走'
      };
    }
    return transition(taskId, 'claim', staffName, function (t) {
      t.assigneeId = staffId;
    });
  }

  function startTask(taskId, actorName) {
    return transition(taskId, 'start', actorName);
  }

  function toggleSop(taskId, itemKey, actorName) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) return { ok: false, code: 'not_found', message: '找不到這筆任務' };
    if (task.state !== 'doing') {
      return { ok: false, code: 'invalid_transition', message: '只有進行中的任務可以勾選 SOP' };
    }
    var item = task.sop.filter(function (s) { return s.key === itemKey; })[0];
    if (!item) return { ok: false, code: 'not_found', message: '找不到這個檢查項' };
    item.checked = !item.checked;
    task.version += 1;
    task.updatedAt = now();
    addEvent(state, taskId, 'sop_toggle', actorName, { item: itemKey, checked: item.checked });
    save(state);
    return { ok: true, task: clone(task) };
  }

  function completeTask(taskId, actorName) {
    var r = transition(taskId, 'complete', actorName);
    if (!r.ok) return r;
    // 流程開關：「打掃免查房」時，完成的打掃單自動走 done→inspected，
    // 等同查驗合格並照樣排 PMS 回寫；事件照記，流水帳看得出是系統自動過的。
    var flow = getState().settings.flow || {};
    if (r.task.type === 'cleaning' && flow.requireInspection === false) {
      var auto = transition(taskId, 'inspect_pass', '系統（免查房設定）', function (t) {
        t.inspectedBy = 'system_auto';
        t.pmsSync = true;
      });
      if (auto.ok) return auto;
    }
    return r;
  }

  function interruptTask(taskId, reason, actorName) {
    return transition(taskId, 'interrupt', actorName, function (t) {
      t.interruptReason = reason;
    });
  }

  function resumeTask(taskId, actorName) {
    return transition(taskId, 'resume', actorName);
  }

  function deferTask(taskId, reason, actorName) {
    return transition(taskId, 'defer', actorName, function (t) {
      t.deferReason = reason;
    });
  }

  function reactivateTask(taskId, actorName) {
    return transition(taskId, 'reactivate', actorName);
  }

  // 罐頭問答是示範「問一下」弱網／免等待設計用的，不走狀態機，純粹提問留痕 + 立即回覆。
  var CANNED_ANSWERS = [
    { q: '這間房可以晚點清嗎？', a: '可以，請在白板標記「延後」，並於兩小時內回來處理。' },
    { q: '住客在房內怎麼辦？', a: '敲門詢問是否方便，不方便就改列延後，稍後再清。' },
    { q: '備品不夠怎麼辦？', a: '先完成其他項目，備品缺口回報櫃檯小陳，由櫃檯協調調貨。' },
    { q: '查房被退回要怎麼做？', a: '依退回項目重新處理，完成後重新整理 SOP 勾選再送查。' }
  ];

  function askQuestion(taskId, questionText, actorName) {
    var state = getState();
    addEvent(state, taskId, 'asked_question', actorName, { question: questionText });
    save(state);
    var hit = CANNED_ANSWERS.filter(function (c) { return questionText.indexOf(c.q.slice(0, 4)) !== -1; })[0];
    return hit ? hit.a : '已記錄你的提問，稍後櫃檯會回覆（SOP 問答示範：點選下方罐頭問題看範例）。';
  }

  /**
   * 查房：itemResults = { bed:true, bathroom:false, amenity:true, qr:true }
   * 任一 false → 整間退回原清房者（done→accepted、redo+1、清空 SOP 勾記）。
   * 全過 → inspected。
   * 清房者 === 查房者 → 403，不做任何變更（在 transition() 之前就擋下，不留事件）。
   */
  function inspectTask(taskId, itemResults, inspectorId, inspectorName) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) return { ok: false, code: 'not_found', message: '找不到這筆任務' };
    if (task.type !== 'cleaning') {
      return { ok: false, code: 'not_inspectable', message: '只有打掃類任務需要查房' };
    }
    if (task.state !== 'done') {
      return { ok: false, code: 'invalid_transition', message: '只有已完成（待查房）的任務可以查房' };
    }
    var allowSelfInspect = state.settings.flow && state.settings.flow.allowSelfInspect === true;
    if (task.assigneeId === inspectorId && !allowSelfInspect) {
      return {
        ok: false,
        code: 'forbidden_self_inspect',
        message: '403：不得查自己清的房，請改派其他人查房'
      };
    }
    // 查房照任務建立當下的 SOP 快照驗，不照現在的租戶設定——設定改了不能回頭
    // 用新清單去判舊任務，不然「少一項就不過」會冤枉已完成的單。
    var items = (task.sop && task.sop.length) ? task.sop : seedSop(state);
    var allPass = items.every(function (i) { return !!itemResults[i.key]; });
    if (allPass) {
      return transition(taskId, 'inspect_pass', inspectorName, function (t) {
        t.inspectedBy = inspectorId;
        t.pmsSync = true;
      });
    }
    var failedItems = items.filter(function (i) { return !itemResults[i.key]; }).map(function (i) { return i.label; });
    return transition(taskId, 'inspect_fail', inspectorName, function (t) {
      t.redoCount = (t.redoCount || 0) + 1;
      t.sop = items.map(function (i) { return { key: i.key, label: i.label, checked: false }; });
      t.failedItems = failedItems;
    });
  }

  // 出廠預設值；實際以租戶設定的 cleaningStandard 為準（settings 頁可調），
  // 這份只當舊狀態補齊前的 fallback。
  var CLEANING_STANDARD = { '退房清': 30, '續住清': 20, '夜床': 10, '空房巡': 5 };

  function cleaningStandardOf(state) {
    return (state.settings && state.settings.cleaningStandard) || CLEANING_STANDARD;
  }

  function floorOf(roomNo) {
    var n = parseInt(roomNo, 10);
    return isNaN(n) ? null : Math.floor(n / 100);
  }

  // 「一房一天只能一筆未完成打掃單」的唯一約束：inspected / cancelled 之外都算未完成
  // ——done 還在等查房，也擋。
  function hasUnfinishedCleaning(state, roomNo) {
    return state.tasks.some(function (t) {
      return t.type === 'cleaning' && t.roomNo === roomNo &&
        t.state !== 'inspected' && t.state !== 'cancelled';
    });
  }

  // 匯入整份覆蓋舊草稿：排房以最後一次匯入為準，PMS 與 Excel 兩個來源混疊會對不了帳。
  // 匯入當下就標記 conflict（該房已有未完成打掃單），讓主管在分配前看得到哪些房不會發佈。
  function importOccupancy(rows, source, actorName) {
    var state = getState();
    var seen = {};
    var draft = [];
    var rejected = [];
    var std = cleaningStandardOf(state);
    (rows || []).forEach(function (r) {
      var roomNo = String(r.roomNo || '').trim();
      var kind = String(r.kind || '').trim();
      if (!/^\d{3,4}$/.test(roomNo) || !(kind in std)) {
        rejected.push((roomNo || '?') + ',' + (kind || '?'));
        return;
      }
      if (seen[roomNo]) return;
      seen[roomNo] = true;
      draft.push({
        roomNo: roomNo,
        floor: floorOf(roomNo),
        kind: kind,
        estMin: std[kind],
        assigneeId: null,
        conflict: hasUnfinishedCleaning(state, roomNo)
      });
    });
    if (!draft.length) {
      return { ok: false, code: 'empty', message: '沒有可匯入的資料' + (rejected.length ? '（' + rejected.length + ' 列格式不符）' : '') };
    }
    state.schedule = { draft: draft, source: source, importedAt: now() };
    addEvent(state, 'schedule', 'occupancy_imported', actorName, { source: source, count: draft.length, rejected: rejected.length });
    save(state);
    return { ok: true, count: draft.length, rejected: rejected };
  }

  // 草稿不是任務：逐列調整負責人不進事件流水，發佈那一刻才算數——
  // 不然主管拉幾次下拉選單，流水帳就被草稿噪音淹掉。
  function setDraftAssignee(roomNo, staffId) {
    var state = getState();
    var row = ((state.schedule || {}).draft || []).filter(function (r) { return r.roomNo === roomNo; })[0];
    if (!row) return { ok: false, code: 'not_found', message: '草稿裡沒有這間房' };
    row.assigneeId = staffId || null;
    save(state);
    return { ok: true };
  }

  // v1 演算法照規格：樓層聚合、一層一人——同層房間綁成一包，整包給目前累計工時
  // 最少的房務員。結果只是建議（不留事件），主管可逐列覆寫後才發佈。
  function autoAssignDraft() {
    var state = getState();
    var draft = (state.schedule || {}).draft || [];
    var rooms = draft.filter(function (r) { return !r.conflict; });
    if (!rooms.length) return { ok: false, code: 'empty', message: '草稿裡沒有可分配的房（先匯入今日房態）' };
    var staff = state.staff.filter(function (s) { return s.dept === 'housekeeping'; });
    if (!staff.length) return { ok: false, code: 'no_staff', message: '沒有房務員可分配' };
    var load = {};
    staff.forEach(function (s) { load[s.id] = 0; });
    var strategy = state.settings.autoAssignStrategy || 'floor';
    if (strategy === 'balance') {
      // 純工時平衡：逐間給目前最閒的人（大工時先排，平衡效果較穩）。代價是同層會拆給多人。
      rooms.slice().sort(function (a, b) { return b.estMin - a.estMin; }).forEach(function (r) {
        var best = staff.slice().sort(function (a, b) { return load[a.id] - load[b.id]; })[0];
        r.assigneeId = best.id;
        load[best.id] += r.estMin;
      });
    } else {
      var floors = {};
      rooms.forEach(function (r) { (floors[r.floor] = floors[r.floor] || []).push(r); });
      Object.keys(floors).sort().forEach(function (f) {
        var pack = floors[f];
        var total = pack.reduce(function (a, r) { return a + r.estMin; }, 0);
        var best = staff.slice().sort(function (a, b) { return load[a.id] - load[b.id]; })[0];
        pack.forEach(function (r) { r.assigneeId = best.id; });
        load[best.id] += total;
      });
    }
    save(state);
    return { ok: true, load: load, strategy: strategy };
  }

  // 發佈＝規格裡唯一「建立即 accepted、不進待接池」的建單路徑。
  // 匯入到發佈之間可能有人另外建了單，所以發佈時逐房重查一次未完成約束，撞到就跳過。
  function publishSchedule(actorName) {
    var state = getState();
    var draft = (state.schedule || {}).draft || [];
    if (!draft.length) return { ok: false, code: 'empty', message: '草稿是空的，先匯入今日房態' };
    var assignable = draft.filter(function (r) { return !r.conflict; });
    var unassigned = assignable.filter(function (r) { return !r.assigneeId; });
    if (unassigned.length) {
      return {
        ok: false, code: 'unassigned',
        message: '還有 ' + unassigned.length + ' 間未分配：' + unassigned.map(function (r) { return r.roomNo; }).join('、')
      };
    }
    var created = 0;
    var skipped = [];
    assignable.forEach(function (r) {
      if (hasUnfinishedCleaning(state, r.roomNo)) { skipped.push(r.roomNo); return; }
      var task = {
        id: uid('task'), roomNo: r.roomNo, dept: 'housekeeping', type: 'cleaning',
        state: 'accepted', assigneeId: r.assigneeId, title: r.kind, detail: '',
        source: 'plan_publish', ruleHit: null, redoCount: 0, sop: seedSop(state),
        rating: null, estMin: r.estMin, version: 1, createdAt: now(), updatedAt: now()
      };
      state.tasks.push(task);
      addEvent(state, task.id, 'created', actorName, { source: 'plan_publish', kind: r.kind });
      addEvent(state, task.id, 'claim', actorName, { from: 'pool', to: 'accepted', note: '排房發佈即指派', assignee: resolveStaffName(state, r.assigneeId) });
      created++;
    });
    draft.filter(function (r) { return r.conflict; }).forEach(function (r) { skipped.push(r.roomNo); });
    state.schedule = { draft: [], source: null, importedAt: null };
    addEvent(state, 'schedule', 'plan_published', actorName, { created: created, skipped: skipped });
    save(state);
    return { ok: true, created: created, skipped: skipped };
  }

  // 改派只開放給「已指派但還沒開始」的單：doing 中換人會丟掉 SOP 進度，
  // 規格把那條路留給 interrupt → 退池，不走改派。
  function reassignTask(taskId, staffId, actorName) {
    var state = getState();
    var task = findTask(state, taskId);
    if (!task) return { ok: false, code: 'not_found', message: '找不到這筆任務' };
    if (task.state !== 'accepted') {
      return { ok: false, code: 'invalid_transition', message: '只有尚未開始（已指派）的任務可以改派' };
    }
    if (task.assigneeId === staffId) {
      return { ok: false, code: 'noop', message: '已經是這位負責人' };
    }
    var fromName = resolveStaffName(state, task.assigneeId);
    task.assigneeId = staffId;
    task.version += 1;
    task.updatedAt = now();
    addEvent(state, taskId, 'reassigned', actorName, { from: fromName, to: resolveStaffName(state, staffId) });
    save(state);
    return { ok: true, task: clone(task) };
  }

  function getSettings() {
    return getState().settings;
  }

  // 改設定也要留事件，否則流水帳解釋不了「這張打掃單為什麼沒人查就 inspected」
  // ——免查房等自動轉移都吃設定值。taskId 哨兵 'settings' 在 findTask 查不到是正常的。
  function updateSettings(mutator, actorName, summary) {
    var state = getState();
    mutator(state.settings);
    addEvent(state, 'settings', 'settings_changed', actorName || '租戶設定', { summary: summary || '' });
    save(state);
    return { ok: true, settings: clone(state.settings) };
  }

  function resetAll() {
    var seeded = global.TMS_SEED.buildSeedState();
    writeRaw(seeded);
    var rev = seeded.rev || 0;
    broadcast(rev);
    notifyLocal();
    return seeded;
  }

  global.TMS_ENGINE = {
    STATES: STATES,
    STATE_LABELS: STATE_LABELS,
    DEPT_LABELS: DEPT_LABELS,
    TYPE_LABELS: TYPE_LABELS,
    INSPECTION_ITEMS: INSPECTION_ITEMS,
    CHECKLIST_RULES: CHECKLIST_RULES,
    CANNED_ANSWERS: CANNED_ANSWERS,
    CLEANING_STANDARD: CLEANING_STANDARD,

    getState: getState,
    getSettings: getSettings,
    updateSettings: updateSettings,
    importOccupancy: importOccupancy,
    setDraftAssignee: setDraftAssignee,
    autoAssignDraft: autoAssignDraft,
    publishSchedule: publishSchedule,
    reassignTask: reassignTask,
    subscribe: subscribe,
    resetAll: resetAll,
    resolveStaffName: resolveStaffName,
    findTask: findTask,
    seedSop: seedSop,

    guestSubmitChecklist: guestSubmitChecklist,
    guestSubmitFreetext: guestSubmitFreetext,
    guestWithdraw: guestWithdraw,
    guestRate: guestRate,

    deskTriage: deskTriage,
    deskAnswer: deskAnswer,
    sendNotice: sendNotice,
    readNotice: readNotice,
    logLostFound: logLostFound,
    returnLostFound: returnLostFound,

    claimTask: claimTask,
    startTask: startTask,
    toggleSop: toggleSop,
    completeTask: completeTask,
    interruptTask: interruptTask,
    resumeTask: resumeTask,
    deferTask: deferTask,
    reactivateTask: reactivateTask,
    askQuestion: askQuestion,

    inspectTask: inspectTask
  };
})(window);
