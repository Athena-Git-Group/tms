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
 * - SLA 逾時升級鏈：任務超過 SLA 後分層通知（L1 接單人／部門池 → L2 主管 → L3 值班經理），
 *   引擎每 5 秒掃一次（scanEscalations），每層只升一次、留 escalated 事件；
 *   升級是通知行為不是新狀態，九態不動。帶 dueAt 的任務改以 dueAt 判逾期。
 *
 * - 自動化規則：觸發源（訂房資料進來／排程時間到）＋條件＋動作（建任務，可設到期）。
 *   訂房觸發在匯入當下比對（generateReservationTasks）、排程觸發吃同一個 5 秒掃描
 *   （scanScheduleRules）；兩者都靠任務上的冪等鍵保證同一個觸發只建一筆。
 *   出廠的訂房屬性準備工單四條就是內建的第一組規則。
 *
 * 不做（demo 口頭說明即可）：429 限流、真 SSE（用 BroadcastChannel 等價替代）。
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

  var ROLE_LABELS = {
    desk: '櫃檯',
    housekeeping: '房務',
    maintenance: '工務',
    manager: '主管',
    owner: '老闆'
  };

  var TYPE_LABELS = {
    cleaning: '打掃',
    amenity: '備品',
    repair: '修繕',
    other: '其他'
  };

  // 角色與權限：角色＝named 權限組，逐動作勾選、按領域分四組。
  // baseFunction 比照既有五個「工作類型」（desk/housekeeping/maintenance/manager/owner），
  // 用來決定這個角色的員工要不要出現在部門待接池／排房欄——權限只管「准不准做」，
  // 不管「屬於哪個工作池」，兩件事分開才不會動到既有搶單／排房的篩選邏輯。
  // live:true 標記的權限在這個 POC 真的端到端生效（settings.html／schedule.html／
  // manager.html 讀得到），其餘權限只記錄勾選狀態，UI 上標「示意」。
  // requiresFlow 標記的整組權限只在對應流程開關開著時才在角色編輯畫面顯示
  // （例如關掉「需要主管查房」，查房權限組就該跟著消失，不然新租戶會看到一堆用不到的勾選框）。
  var PERMISSION_GROUPS = [
    {
      key: 'task_ops', label: '任務操作', items: [
        { key: 'task_claim', label: '認領任務（搶單）' },
        { key: 'task_start', label: '開始任務' },
        { key: 'task_complete', label: '完成任務' },
        { key: 'task_cancel', label: '取消任務' }
      ]
    },
    {
      key: 'schedule_ops', label: '排房', items: [
        { key: 'schedule_pull_pms', label: '從 PMS 拉今日房態' },
        { key: 'schedule_import_excel', label: '匯入 Excel（CSV）' },
        { key: 'schedule_publish', label: '發佈排房' },
        { key: 'schedule_reassign', label: '改派（排房看板）', live: true }
      ]
    },
    {
      key: 'inspection_ops', label: '查房與品質', requiresFlow: 'requireInspection', items: [
        { key: 'inspect_submit', label: '查房判定', live: true },
        { key: 'view_ratings', label: '查看住客評分' }
      ]
    },
    {
      key: 'admin_ops', label: '管理', items: [
        { key: 'notice_send', label: '通知板發送' },
        { key: 'lostfound_return', label: '遺失物歸還' },
        { key: 'tenant_settings', label: '租戶設定', live: true },
        { key: 'owner_report', label: '老闆報表' },
        { key: 'qr_regenerate', label: 'QR 重產', live: true }
      ]
    }
  ];

  var ALL_PERM_KEYS = [];
  var PERMISSION_LABELS = {};
  PERMISSION_GROUPS.forEach(function (g) {
    g.items.forEach(function (it) {
      ALL_PERM_KEYS.push(it.key);
      PERMISSION_LABELS[it.key] = it.label;
    });
  });

  function blankPermissions(value) {
    var p = {};
    ALL_PERM_KEYS.forEach(function (k) { p[k] = !!value; });
    return p;
  }

  function permsFrom(trueKeys) {
    var p = blankPermissions(false);
    (trueKeys || []).forEach(function (k) { p[k] = true; });
    return p;
  }

  // 五個工作類型的建議權限範本＋Administrator 全開；「套用範本」與出廠預設角色
  // 共用同一份表，改一處兩邊都跟著變。
  var ROLE_TEMPLATES = {
    administrator: blankPermissions(true),
    desk: permsFrom(['view_ratings', 'notice_send', 'lostfound_return']),
    // 查房者下拉刻意放房務人員進去是為了示範「不得查自己清的房」403；
    // 403 擋的是自查這個業務規則，不是房務這個角色不能查房，兩條規則分開才看得出各自在擋什麼，
    // 所以這裡給查房判定權限，角色權限不替代、也不混淆那條 403 規則。
    housekeeping: permsFrom(['task_claim', 'task_start', 'task_complete', 'inspect_submit']),
    maintenance: permsFrom(['task_claim', 'task_start', 'task_complete', 'inspect_submit']),
    manager: permsFrom([
      'task_cancel', 'schedule_pull_pms', 'schedule_import_excel', 'schedule_publish',
      'schedule_reassign', 'inspect_submit', 'view_ratings', 'notice_send',
      'lostfound_return', 'owner_report'
    ]),
    owner: permsFrom(['view_ratings', 'tenant_settings', 'owner_report'])
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

  // 跟 settings 平級的頂層欄位各自的出廠預設集中表列在這裡：逐欄位各寫一段 if
  // 容易漏（staff 就漏過一次，缺鍵時整頁對著 undefined 呼叫 .filter 直接炸掉），
  // 以後加新頂層欄位只要在這裡多一行，不用再散落各處各寫一段遷移邏輯。
  var TOP_LEVEL_DEFAULTS = {
    schedule: function () { return { draft: [], excluded: [], source: null, importedAt: null }; },
    rooms: function () { return global.TMS_SEED.buildDefaultRooms(); },
    staff: function () { return clone(global.TMS_SEED.STAFF); },
    roles: function () { return buildDefaultRoles(); }
  };

  // 出廠角色：Administrator（全開、builtin、比照主管工作類型）＋五個工作類型各一個
  // 建議角色（key 直接等於工作類型值，讓「舊員工掛對應角色」的遷移只要查表，
  // 不用另外維護一份對照）。租戶可以改名、調權限、停用，但不能刪——跟部門／
  // 房間／員工同一套「只能停用」的慣例。
  function buildDefaultRoles() {
    return ['administrator', 'desk', 'housekeeping', 'maintenance', 'manager', 'owner'].map(function (key) {
      return {
        key: key,
        name: key === 'administrator' ? 'Administrator' : ROLE_LABELS[key],
        baseFunction: key === 'administrator' ? 'manager' : key,
        builtin: key === 'administrator',
        active: true,
        permissions: clone(ROLE_TEMPLATES[key]),
        createdAt: now()
      };
    });
  }

  // 清掃標準從一維「類型→分鐘」升級成「類型×房態」基準表的 migration。
  // 直接拿出廠表會丟失租戶調過的數字，所以以出廠表當底，再把既有一維值
  // 搬進各類型推定房態的那一格（退房清→退房、夜床→續住……）——那一格正是
  // 舊制那個數字實際在量的情境。已有基準表的狀態不重複搬。
  function migrateCleaningMatrix(settings, defaults) {
    if (settings.cleaningStandardMatrix) return false;
    var matrix = clone(defaults.cleaningStandardMatrix);
    var legacy = settings.cleaningStandard || {};
    Object.keys(legacy).forEach(function (kind) {
      if (!(legacy[kind] > 0)) return;
      matrix[kind] = matrix[kind] || {};
      matrix[kind][defaultRoomStateOf(kind)] = legacy[kind];
    });
    settings.cleaningStandardMatrix = matrix;
    return true;
  }

  // 「準備工單規則」泛化成「自動化規則」的 migration：舊鍵 prepTaskRules 整批搬到
  // automationRules、逐條補 trigger='reservation'（舊規則全是訂房觸發）與 dueAfterMin，
  // 租戶調過的規則內容原樣保留；跟出廠 key 對得上的補 builtin 標記。沒有舊鍵的
  // 全新狀態直接用出廠預設。跟 migrateCleaningMatrix 一樣得搶在通用補鍵之前跑，
  // 不然通用補鍵會直接塞出廠值、把租戶自建的舊規則洗掉。
  function migrateAutomationRules(settings, defaults) {
    var patched = false;
    if (!settings.automationRules) {
      settings.automationRules = settings.prepTaskRules ? clone(settings.prepTaskRules) : clone(defaults.automationRules);
      patched = true;
    }
    var builtinKeys = defaults.automationRules.map(function (r) { return r.key; });
    settings.automationRules.forEach(function (r) {
      if (!r.trigger) { r.trigger = 'reservation'; patched = true; }
      if (r.dueAfterMin === undefined) { r.dueAfterMin = null; patched = true; }
      if (r.builtin === undefined && builtinKeys.indexOf(r.key) !== -1) { r.builtin = true; patched = true; }
    });
    if (settings.prepTaskRules) { delete settings.prepTaskRules; patched = true; }
    return patched;
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
      // 基準表 migration 必須搶在通用補鍵之前跑：通用補鍵對缺鍵一律整鍵塞
      // 出廠值，沒有「帶著既有 cleaningStandard 的值去建表」的能力，順序顛倒
      // 等於把租戶調過的標準工時默默洗回出廠。
      var patched = migrateCleaningMatrix(s.settings, defaults);
      if (migrateAutomationRules(s.settings, defaults)) patched = true;
      Object.keys(defaults).forEach(function (k) {
        if (!(k in s.settings)) { s.settings[k] = defaults[k]; patched = true; }
      });
      if (patched) writeRaw(s);
    }
    var topPatched = false;
    Object.keys(TOP_LEVEL_DEFAULTS).forEach(function (k) {
      if (!s[k]) { s[k] = TOP_LEVEL_DEFAULTS[k](); topPatched = true; }
    });
    if (topPatched) writeRaw(s);
    // 員工逐筆補欄位（不是頂層欄位，TOP_LEVEL_DEFAULTS 表管不到）：舊狀態的員工
    // 沒有 roleKey／email／invite，掛對應角色（role 當 key 查，查不到就退回櫃檯）、
    // email 留空、視為「已經啟用過」（不然既有在職員工會突然被當成待開通）。
    var staffPatched = false;
    (s.staff || []).forEach(function (st) {
      // roleKey 不保證指得到現存角色——角色可以被整批重建或單獨刪除。查無
      // 現存角色一律退回依職能（st.role）查預設角色，查不到才退回櫃檯，
      // 否則員工清單會印出查無名稱的原始角色鍵字串。
      var roleDangling = st.roleKey !== undefined && !(s.roles || []).some(function (r) { return r.key === st.roleKey; });
      if (st.roleKey === undefined || roleDangling) {
        st.roleKey = (s.roles || []).some(function (r) { return r.key === st.role; }) ? st.role : 'desk';
        staffPatched = true;
      }
      if (st.email === undefined) { st.email = ''; staffPatched = true; }
      if (st.invite === undefined) {
        st.invite = { status: 'activated', token: null, invitedAt: null, activatedAt: null };
        staffPatched = true;
      }
      if (st.active === undefined) { st.active = true; staffPatched = true; }
    });
    if (staffPatched) writeRaw(s);
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

  // 部門顯示名稱：租戶可以在設定頁把部門改名（例如「房務」改叫「客房」），
  // 任務卡上的部門標籤要跟著變——所以一律查目前設定的部門清單，查不到才退回
  // 出廠的 DEPT_LABELS（desk 等非「可管理部門」的內建值），都查不到就顯示原始 key。
  function deptLabel(dept) {
    if (!dept) return dept;
    var state = getState();
    var d = (state.settings.departments || []).filter(function (x) { return x.key === dept; })[0];
    if (d) return d.name;
    return DEPT_LABELS[dept] || dept;
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

  // 防濫用節流：吃「營業參數」設定的時窗／上限，逐次重算（不開另一條計數器狀態），
  // 調大參數立刻生效、調小立刻收緊，不用等視窗滑動。
  function guestThrottleExceeded(state, roomNo) {
    var biz = state.settings.business || {};
    var windowMin = biz.guestThrottleWindowMin > 0 ? biz.guestThrottleWindowMin : 10;
    var maxCount = biz.guestThrottleMaxCount > 0 ? biz.guestThrottleMaxCount : 3;
    var cutoff = now() - windowMin * 60000;
    var count = state.tasks.filter(function (t) {
      return t.roomNo === roomNo &&
        (t.source === 'guest_checklist' || t.source === 'guest_freetext') &&
        t.createdAt >= cutoff;
    }).length;
    return count >= maxCount;
  }

  function guestSubmitChecklist(roomNo, ruleKey) {
    var state = getState();
    if (guestThrottleExceeded(state, roomNo)) {
      return { ok: false, code: 'rate_limited', message: '這個房間短時間內送出太多需求，請稍後再試（或直接洽櫃檯）' };
    }
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
    if (guestThrottleExceeded(state, roomNo)) {
      return { ok: false, code: 'rate_limited', message: '這個房間短時間內送出太多需求，請稍後再試（或直接洽櫃檯）' };
    }
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
    // 老闆通知來源開關要在「產生當下」判斷，不是顯示當下判斷——不然關掉期間
    // 發生的低分評價，事後重新開啟會回溯冒出，老闆會誤以為是剛剛才發生的事。
    // 評分本身照樣記錄（住客評分統計不受通知開關影響），只有「這筆算不算夠格
    // 進老闆通知」這個旗標照當下設定值釘住，owner.html 用這個旗標篩選，不是
    // 用現在的開關狀態篩選。
    var ownerNotifyEligible = (state.settings.notifications || {}).ownerLowRatingEnabled !== false;
    return transition(taskId, 'guest_rate', '住客 ' + task.roomNo, function (t) {
      t.rating = stars;
      return { stars: stars, ownerNotifyEligible: ownerNotifyEligible };
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

  // 主管上報老闆：獨立事件字彙（不是 settings_changed），taskId 哨兵 'owner_report'
  // 在 findTask 查不到是正常的——owner.html 直接篩事件，不經任務投影。來源開關關閉時
  // 仍防呆擋一次（UI 本身也會先行停用按鈕），兩層防線同一套訊息。
  function reportToOwner(message, actorName) {
    var text = (message || '').trim();
    if (!text) return { ok: false, code: 'empty', message: '請輸入要上報的內容' };
    var state = getState();
    if (!(state.settings.notifications || {}).ownerManagerReportEnabled) {
      return { ok: false, code: 'disabled', message: '老闆通知目前關閉「主管上報」來源（到租戶設定「通知」開啟）' };
    }
    addEvent(state, 'owner_report', 'manager_escalation', actorName || '主管', { message: text });
    save(state);
    return { ok: true };
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
      ruleHit: c.title + ' → ' + deptLabel(c.dept) + '待接池（自建項目）'
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
      var auto = transition(taskId, 'inspect_pass', '系統（免查房設定）', function (t, st) {
        t.inspectedBy = 'system_auto';
        // 免查房的自動合格跟人工查房吃同一個回寫開關：關著就不排回寫，
        // 不能因為是系統自動過的就繞過租戶的 PMS 整合設定。
        t.pmsSync = pmsWritebackEnabled(st.settings);
      });
      if (auto.ok) {
        if (auto.task.pmsSync) {
          recordPmsWriteback(taskId, auto.task.roomNo + ' 號房免查房自動合格，房間狀態回寫 Inspected（模擬，已排進待送匣）');
        }
        return auto;
      }
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
      var passed = transition(taskId, 'inspect_pass', inspectorName, function (t, st) {
        t.inspectedBy = inspectorId;
        // pmsSync 不再寫死 true：回寫開關關閉（或手動匯入模式沒有回寫對象）時
        // 查房照樣合格，只是不排回寫——旗標照實記，查房頁 toast 才講得出
        // 「這次有沒有回寫」。
        t.pmsSync = pmsWritebackEnabled(st.settings);
      });
      if (passed.ok && passed.task.pmsSync) {
        recordPmsWriteback(taskId, passed.task.roomNo + ' 號房查房通過，房間狀態回寫 Inspected（模擬，已排進待送匣）');
      }
      return passed;
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

  // 房態四值與顯示名稱。匯入資料與設定的基準表共用同一套值，不另設縮寫。
  var ROOM_STATES = ['checkout', 'stayover', 'vacant', 'out_of_service'];
  var ROOM_STATE_LABELS = {
    checkout: '退房',
    stayover: '續住',
    vacant: '空房',
    out_of_service: '停用維修'
  };

  // 房間當日旗標二值與顯示名稱。旗標不是房態：環保牌可以掛在續住房上、請勿打擾
  // 可以掛在任何住人的房上，硬塞進房態四值會把兩個獨立維度壓成一個。
  // v1 旗標只決定進不進排房草稿（吃 scheduleInclusion 開關），不影響預估工時。
  var ROOM_FLAGS = ['eco', 'do_not_disturb'];
  var ROOM_FLAG_LABELS = {
    eco: '環保房',
    do_not_disturb: '請勿打擾'
  };

  // 匯入資料的旗標欄同時接受值（eco）與中文講法（環保／環保房）：CSV 是給人手打的。
  // 認不得的字樣直接丟棄而不是整列退件——旗標是加值資訊，打錯一個詞不該讓整間房
  // 進不了排房。
  function normalizeRoomFlags(raw) {
    var list = Array.isArray(raw) ? raw : [];
    var out = [];
    list.forEach(function (f) {
      var v = String(f || '').trim();
      if (!v) return;
      var hit = ROOM_FLAG_LABELS[v] ? v
        : (v === '環保' || v === '環保房' ? 'eco'
          : (v === '請勿打擾' || v === 'DND' || v === 'dnd' ? 'do_not_disturb' : null));
      if (hit && out.indexOf(hit) === -1) out.push(hit);
    });
    return out;
  }

  // 納入開關缺鍵時退回出廠值逐鍵補：舊 localStorage 只會缺整個 scheduleInclusion
  // 鍵（通用補鍵會補），但第三方直接改過 localStorage 的半套物件也要能用。
  function scheduleInclusionOf(settings) {
    var defaults = global.TMS_SEED.defaultSettings().scheduleInclusion;
    var cur = (settings || {}).scheduleInclusion || {};
    var out = {};
    Object.keys(defaults).forEach(function (k) {
      out[k] = (k in cur) ? cur[k] !== false : defaults[k];
    });
    return out;
  }

  // PMS 同步模式顯示名稱：值跟 seed 的 PMS_PROVIDERS.mode 同一套，不另設縮寫。
  var PMS_MODE_LABELS = {
    push: '推事件（事件訂閱）',
    poll: '輪詢',
    manual: '手動匯入'
  };

  // PMS 設定缺鍵時退回出廠值逐鍵補：舊 localStorage 只會缺整個 pms 鍵（getState
  // 的通用補鍵會補成出廠值＝自家 PMS），但第三方直接改過 localStorage 的半套
  // 物件也要能用——跟 scheduleInclusionOf 同一套防禦。
  function pmsSettingsOf(settings) {
    var defaults = global.TMS_SEED.defaultSettings().pms;
    var cur = (settings || {}).pms || {};
    return {
      provider: cur.provider || defaults.provider,
      credential: typeof cur.credential === 'string' ? cur.credential : defaults.credential,
      capability: cur.capability || null,
      syncScope: {
        occupancy: (cur.syncScope || {}).occupancy !== false,
        reservationAttributes: (cur.syncScope || {}).reservationAttributes !== false
      },
      writeback: {
        roomStatus: (cur.writeback || {}).roomStatus !== false,
        minibarBilling: (cur.writeback || {}).minibarBilling !== false
      }
    };
  }

  // 「房間狀態」這條回寫路徑現在排不排得出去：開關要開著、而且真的有接一家
  // PMS（手動匯入模式沒有回寫對象，開關開著也無處可寫）。
  function pmsWritebackEnabled(settings) {
    var pms = pmsSettingsOf(settings);
    return pms.writeback.roomStatus && pms.provider !== 'manual';
  }

  function pmsProviderOf(providerKey) {
    return (global.TMS_SEED.PMS_PROVIDERS || []).filter(function (p) { return p.key === providerKey; })[0] || null;
  }

  // 「測試連線＋能力盤點」：mock 查 seed 的能力表，把盤點結果存回設定。真實環境
  // 是 adapter 去敲 PMS 的 API 問它支不支援 webhook／事件訂閱，這裡直接查表示意。
  // 盤點是一個有結果的動作（跟 occupancy_imported 同類），用獨立事件字彙留痕，
  // 不混進 settings_changed——流水帳要看得出「誰在什麼時候盤點過、結果是什麼」。
  function testPmsConnection(actorName) {
    var state = getState();
    var pms = pmsSettingsOf(state.settings);
    var provider = pmsProviderOf(pms.provider);
    if (!provider) return { ok: false, code: 'unknown_provider', message: '未知的 PMS，先選一個再盤點' };
    if (provider.mode === 'manual') {
      return { ok: false, code: 'manual_mode', message: '手動匯入模式沒有要連 PMS，不用測試連線——今日房態改用排房頁的 Excel／CSV 匯入' };
    }
    if (!pms.credential) {
      return { ok: false, code: 'no_credential', message: '連線憑證是空的，先填一組（示意值即可）再測試' };
    }
    var capability = {
      provider: provider.key,
      mode: provider.mode,
      pollIntervalMin: provider.pollIntervalMin,
      checkedAt: now()
    };
    state.settings.pms = state.settings.pms || {};
    state.settings.pms.capability = capability;
    addEvent(state, 'settings', 'pms_capability_checked', actorName || '租戶設定', {
      provider: provider.name,
      mode: provider.mode,
      summary: provider.name + ' 連線成功；能力盤點：' + PMS_MODE_LABELS[provider.mode]
        + (provider.mode === 'poll' ? '（每 ' + provider.pollIntervalMin + ' 分鐘）' : '')
    });
    save(state);
    return { ok: true, capability: capability, provider: provider };
  }

  // 模擬 PMS 回寫：對應 docs/11「回寫永遠只有既定兩條路徑」的「房間狀態」那條
  // （查房通過 Inspected、停用維修 OOO——POC 的既有流程只走得到 Inspected）。
  // 回寫開關關閉、或根本沒接 PMS（手動匯入模式）時不記回寫事件；開著就補一筆
  // pms_writeback 當「已排進待送匣、由 adapter 送出」的示意留痕。獨立於
  // inspect_pass 事件之後才記，流水帳的順序才對得上真實系統「先改狀態、
  // 再排回寫」的因果。
  function recordPmsWriteback(taskId, detail) {
    var state = getState();
    var pms = pmsSettingsOf(state.settings);
    if (!pms.writeback.roomStatus || pms.provider === 'manual') return false;
    var provider = pmsProviderOf(pms.provider);
    addEvent(state, taskId, 'pms_writeback', '系統（PMS adapter）', {
      path: 'room_status',
      provider: provider ? provider.name : pms.provider,
      summary: detail || '房間狀態回寫 Inspected（模擬，已排進待送匣）'
    });
    save(state);
    return true;
  }

  // 一列今日房態要不要被排除在草稿外。回傳排除原因清單（空陣列＝納入）：
  // 一間房可能同時命中兩個排除條件（請勿打擾的停用維修房），只報第一個原因
  // 的話，主管開了其中一個開關重拉，房還是不見、卻查不出第二個原因。
  function scheduleExclusionReasons(inclusion, roomState, flags) {
    var reasons = [];
    if (roomState === 'vacant' && !inclusion.vacant) reasons.push('空房（開關：排除）');
    if (roomState === 'out_of_service' && !inclusion.out_of_service) reasons.push('停用維修房（開關：排除）');
    (flags || []).forEach(function (f) {
      if (f === 'eco' && !inclusion.eco) reasons.push('環保房（開關：排除）');
      if (f === 'do_not_disturb' && !inclusion.do_not_disturb) reasons.push('請勿打擾（開關：排除）');
    });
    return reasons;
  }

  // 舊匯入格式沒有房態欄。依清掃類型推定最自然的房態（退房清就是退房房、
  // 夜床只發生在有人續住的房），舊 CSV 與舊 PMS 模擬資料照常可匯入。
  var KIND_DEFAULT_ROOM_STATE = { '退房清': 'checkout', '續住清': 'stayover', '夜床': 'stayover', '空房巡': 'vacant' };

  function defaultRoomStateOf(kind) {
    return KIND_DEFAULT_ROOM_STATE[kind] || 'vacant';
  }

  // 匯入資料的房態欄同時接受值（checkout）與中文顯示名（退房）：CSV 是給人手打的，
  // 要求打英文值只會打錯。認不得或沒填就依清掃類型推定。
  function normalizeRoomState(raw, kind) {
    var v = String(raw || '').trim();
    if (ROOM_STATE_LABELS[v]) return v;
    var byLabel = ROOM_STATES.filter(function (k) { return ROOM_STATE_LABELS[k] === v; })[0];
    return byLabel || defaultRoomStateOf(kind);
  }

  /**
   * 這間房這種清掃要幾分鐘。取值順序固定是三層：
   * 房間覆寫 > 類型×房態基準格 > 類型預設。
   * 覆寫寫在房間自己身上（306 退房清就是 40 分），基準格抓「同類型在不同房態
   * 工時不同」的差異，兩層都取不到才退回全館一套的類型預設——順序反了的話，
   * 特殊房間的真實工時會被基準值蓋掉，排房的負載又開始失真。
   */
  function estimateCleaningMinutes(state, roomNo, kind, roomState) {
    var settings = state.settings || {};
    var override = (settings.cleaningRoomOverrides || {})[roomNo];
    if (override && override[kind] > 0) return override[kind];
    var row = (settings.cleaningStandardMatrix || {})[kind];
    var st = roomState || defaultRoomStateOf(kind);
    if (row && row[st] > 0) return row[st];
    return cleaningStandardOf(state)[kind] || 0;
  }

  // 工作量數字後面要掛的單位字。底層一律存分鐘，租戶選「點數」時只是換標籤、
  // 數字原樣——所有顯示處都從這裡拿字，才不會這頁寫「分」那頁寫「點」。
  function workloadUnitLabel(settings) {
    return (settings && settings.workloadDisplayUnit === 'points') ? '點' : '分';
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

  // 當地日期字串（不含時區換算，POC 沒有真正的跨日資料）：準備工單幂等鍵
  // 「房號＋規則＋天」用這個當「天」，同一天重複拉房態／匯入不會重複開單。
  function todayKeyOf() {
    var d = new Date();
    return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate();
  }

  function normAttr(a) {
    return String(a || '').trim().toLowerCase();
  }

  // 自動化規則（訂房觸發）：排房匯入（PMS／CSV）當下，對帶屬性的房間逐一比對
  // 目前啟用中、trigger=reservation 的規則，命中就直接進對應部門待接池（不是
  // triage、不是另一種事件——沿用既有 created 事件字彙，來源標記放 ruleHit／
  // payload，狀態機語意不變）。出廠四條內建規則（準備工單）走的就是這條路。
  // 幂等鍵＝房號＋規則＋當地日期：同一天重複拉房態不會重複開單；規則停用後
  // 不會再比對到，但已經建立的單不會被收回（跟部門/角色「停用不影響既存指派」
  // 同一套精神）。
  function generateReservationTasks(state, rows, actorName) {
    var rules = (state.settings.automationRules || []).filter(function (r) {
      return r.trigger === 'reservation' && r.enabled !== false;
    });
    if (!rules.length) return 0;
    var todayKey = todayKeyOf();
    var created = 0;
    (rows || []).forEach(function (row) {
      var attrs = (row.attributes || []).map(normAttr);
      if (!attrs.length) return;
      rules.forEach(function (rule) {
        if (attrs.indexOf(normAttr(rule.attribute)) === -1) return;
        var dedupeKey = row.roomNo + '|' + rule.key + '|' + todayKey;
        var already = state.tasks.some(function (t) { return t.prepDedupeKey === dedupeKey; });
        if (already) return;
        var task = {
          id: uid('task'), roomNo: row.roomNo, dept: rule.dept, type: rule.type,
          state: 'pool', assigneeId: null, title: rule.title, detail: '',
          source: 'prep_rule', ruleHit: '訂房屬性「' + rule.attribute + '」→ ' + deptLabel(rule.dept) + '待接池（自動化規則：訂房觸發）',
          redoCount: 0, sop: rule.type === 'cleaning' ? seedSop(state) : [],
          rating: null, version: 1, createdAt: now(), updatedAt: now(),
          prepDedupeKey: dedupeKey, prepRuleKey: rule.key, prepAttribute: rule.attribute
        };
        if (rule.dueAfterMin > 0) task.dueAt = task.createdAt + rule.dueAfterMin * 60000;
        state.tasks.push(task);
        addEvent(state, task.id, 'created', actorName || '系統（自動化規則）', {
          source: 'prep_rule', ruleKey: rule.key, attribute: rule.attribute, roomNo: row.roomNo,
          note: '自動化規則（' + rule.title + '）自動建立'
        });
        created++;
      });
    });
    return created;
  }

  // 匯入整份覆蓋舊草稿：排房以最後一次匯入為準，PMS 與 Excel 兩個來源混疊會對不了帳。
  // 匯入當下就標記 conflict（該房已有未完成打掃單），讓主管在分配前看得到哪些房不會發佈。
  // 同一次匯入也順便對帶訂房屬性的房間跑一次準備工單規則——這兩件事都是「今日房態
  // 進來了」這同一個時間點該做的事，不分兩次操作，租戶不用另外按一個按鈕。
  function importOccupancy(rows, source, actorName) {
    var state = getState();
    var pms = pmsSettingsOf(state.settings);
    // 從 PMS 拉回走同步範圍開關：手動匯入模式根本沒接 PMS、「今日房態」關閉
    // 代表這條同步路徑整個不拉——都在引擎擋（不只停用按鈕），第三方直接呼叫
    // importOccupancy 也繞不過設定。Excel／CSV 是降級用的人工路徑，不受這兩個
    // 開關管：PMS 接不上時還要能匯入，是這條路存在的理由。
    if (source === 'pms') {
      if (pms.provider === 'manual') {
        return { ok: false, code: 'pms_manual_mode', message: '目前是手動匯入模式（沒有接 PMS），請改用 Excel／CSV 匯入今日房態' };
      }
      if (!pms.syncScope.occupancy) {
        return { ok: false, code: 'pms_scope_off', message: '同步範圍「今日房態」已關閉，PMS 不會拉回房態——到租戶設定「PMS 整合」開回來，或改用 Excel／CSV 匯入' };
      }
    }
    var seen = {};
    var draft = [];
    var excluded = [];
    var rejected = [];
    var prepRows = [];
    var attributesDropped = 0;
    var std = cleaningStandardOf(state);
    var inclusion = scheduleInclusionOf(state.settings);
    (rows || []).forEach(function (r) {
      var roomNo = String(r.roomNo || '').trim();
      var kind = String(r.kind || '').trim();
      var attributes = (Array.isArray(r.attributes) ? r.attributes : [])
        .map(function (a) { return String(a || '').trim(); })
        .filter(Boolean);
      // 同步範圍「訂房屬性」關閉＝這個維度整個不進系統：匯入當下就丟棄屬性欄
      //（PMS 拉回與 Excel 匯入一視同仁），於是訂房觸發的自動化規則自然不命中、
      // 草稿卡也不帶屬性——不是在規則引擎那端另設一個「略過」分支，單一事實
      // 來源就是「屬性沒進來」。
      if (!pms.syncScope.reservationAttributes && attributes.length) {
        attributesDropped += 1;
        attributes = [];
      }
      if (!/^\d{3,4}$/.test(roomNo) || !(kind in std)) {
        rejected.push((roomNo || '?') + ',' + (kind || '?'));
        return;
      }
      // 停用的房間不進排房：房間主檔裡找得到、且被標記停用才擋下；查不到的房號
      // （不在主檔裡）維持舊行為放行，CSV 匯入仍可以自由打房號示範。
      var roomRec = (state.rooms || []).filter(function (r) { return r.roomNo === roomNo; })[0];
      if (roomRec && roomRec.active === false) {
        rejected.push(roomNo + ',房間已停用');
        return;
      }
      if (seen[roomNo]) return;
      seen[roomNo] = true;
      var roomState = normalizeRoomState(r.roomState, kind);
      var flags = normalizeRoomFlags(r.flags);
      // 準備工單規則在排除判斷之前跑：納入開關管的是「要不要排人去掃」，
      // 訂房屬性命中的準備工單（VIP 迎賓…）是另一回事——請勿打擾的 VIP 房
      // 照樣要備迎賓品，只是別排打掃。
      if (attributes.length) prepRows.push({ roomNo: roomNo, attributes: attributes });
      var reasons = scheduleExclusionReasons(inclusion, roomState, flags);
      if (reasons.length) {
        excluded.push({
          roomNo: roomNo, kind: kind, roomState: roomState, flags: flags,
          reasons: reasons
        });
        return;
      }
      draft.push({
        roomNo: roomNo,
        floor: floorOf(roomNo),
        kind: kind,
        roomState: roomState,
        flags: flags,
        estMin: estimateCleaningMinutes(state, roomNo, kind, roomState),
        assigneeId: null,
        conflict: hasUnfinishedCleaning(state, roomNo),
        attributes: attributes
      });
    });
    // 整批都被開關排除時仍要落盤：排房頁要能列出「0 間進草稿、N 間被設定排除」，
    // 直接回錯的話主管只會看到空草稿、查不出房去了哪裡。
    if (!draft.length && !excluded.length) {
      return { ok: false, code: 'empty', message: '沒有可匯入的資料' + (rejected.length ? '（' + rejected.length + ' 列格式不符）' : '') };
    }
    state.schedule = { draft: draft, excluded: excluded, source: source, importedAt: now() };
    var prepTasksCreated = generateReservationTasks(state, prepRows, actorName);
    addEvent(state, 'schedule', 'occupancy_imported', actorName, {
      source: source, count: draft.length, excluded: excluded.length, rejected: rejected.length,
      prepTasksCreated: prepTasksCreated, attributesDropped: attributesDropped
    });
    save(state);
    return { ok: true, count: draft.length, excluded: excluded, rejected: rejected, prepTasksCreated: prepTasksCreated, attributesDropped: attributesDropped };
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
    var staff = state.staff.filter(function (s) { return s.role === 'housekeeping' && s.active !== false; });
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
        rating: null, estMin: r.estMin, roomState: r.roomState, version: 1, createdAt: now(), updatedAt: now()
      };
      state.tasks.push(task);
      addEvent(state, task.id, 'created', actorName, { source: 'plan_publish', kind: r.kind });
      addEvent(state, task.id, 'claim', actorName, { from: 'pool', to: 'accepted', note: '排房發佈即指派', assignee: resolveStaffName(state, r.assigneeId) });
      created++;
    });
    draft.filter(function (r) { return r.conflict; }).forEach(function (r) { skipped.push(r.roomNo); });
    state.schedule = { draft: [], excluded: [], source: null, importedAt: null };
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

  // updateSettings 的 mutator 只能碰 state.settings；員工與房間主檔跟 state.staff
  // 一樣是頂層欄位，需要一個能碰整份 state 的版本，事件字彙仍沿用 settings_changed。
  function mutateState(mutator, actorName, summary) {
    var state = getState();
    mutator(state);
    addEvent(state, 'settings', 'settings_changed', actorName || '租戶設定', { summary: summary || '' });
    save(state);
    return { ok: true, state: clone(state) };
  }

  var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  // 新增員工＝邀請：只填姓名／email／角色，先進「待開通」（active:false），
  // 不寄真信——activateStaff 模擬使用者點開通連結。角色決定 baseFunction，
  // baseFunction 才決定要不要問部門（housekeeping/maintenance 才需要）、
  // 也才決定 staff.role（既有搶單／排房／查房池篩選吃的就是這個欄位，維持不變）。
  function addStaff(data, actorName) {
    var name = (data.name || '').trim();
    if (!name) return { ok: false, code: 'invalid', message: '姓名不能空白' };
    var email = (data.email || '').trim();
    if (!email || !EMAIL_RE.test(email)) return { ok: false, code: 'invalid', message: '請輸入有效的 email' };
    var state = getState();
    var role = (state.roles || []).filter(function (r) { return r.key === data.roleKey; })[0];
    if (!role) return { ok: false, code: 'invalid', message: '請選擇角色' };
    if (role.active === false) return { ok: false, code: 'invalid', message: '這個角色已停用，無法指派給新員工' };
    var baseFunction = role.baseFunction;
    var dept = (baseFunction === 'housekeeping' || baseFunction === 'maintenance') ? (data.dept || baseFunction) : null;
    var staff = {
      id: uid('stf'), name: name, email: email, role: baseFunction, roleKey: role.key, dept: dept,
      active: false,
      invite: { status: 'pending', token: uid('inv'), invitedAt: now(), activatedAt: null }
    };
    mutateState(function (st) { st.staff.push(staff); }, actorName, '邀請員工「' + name + '」（角色：' + role.name + '）');
    return { ok: true, staff: staff };
  }

  // 編輯既有員工：姓名／email／角色／部門皆可改。角色決定 baseFunction，
  // baseFunction 才決定要不要問部門、也才決定 staff.role（既有搶單／排房／查房
  // 池篩選吃的就是這個欄位）——跟 addStaff 共用同一套規則，不能新增跟編輯兩邊
  // 各自一套邏輯。沒傳 dept 時用新角色的 baseFunction 當預設值（角色職能帶入），
  // 傳了就用傳入值（使用者在畫面上手動覆寫過）。
  // 已經接下的任務在建立當下就把 dept 釘死在自己的欄位上，不會因為員工後來改
  // 角色／部門而被追著搬動；排房欄、待接池、改派名單都是「現查 state.staff」，
  // 下一次重繪就自動反映新的歸屬，不用額外同步。
  function updateStaff(staffId, data, actorName) {
    var state = getState();
    var s = (state.staff || []).filter(function (x) { return x.id === staffId; })[0];
    if (!s) return { ok: false, code: 'not_found', message: '找不到這位員工' };
    var name = (data.name || '').trim();
    if (!name) return { ok: false, code: 'invalid', message: '姓名不能空白' };
    // 編輯跟邀請不同：邀請一定要留 email 才寄得出開通連結，編輯則是「可以補／
    // 改」，不強制既有員工（含舊資料沒留 email 的）一定要先補好才能動其他欄位；
    // 有填就照格式驗，留空允許。
    var email = (data.email || '').trim();
    if (email && !EMAIL_RE.test(email)) return { ok: false, code: 'invalid', message: '請輸入有效的 email' };
    var role = (state.roles || []).filter(function (r) { return r.key === data.roleKey; })[0];
    if (!role) return { ok: false, code: 'invalid', message: '請選擇角色' };
    if (role.active === false) return { ok: false, code: 'invalid', message: '這個角色已停用，無法指派給員工' };
    var baseFunction = role.baseFunction;
    var dept = (baseFunction === 'housekeeping' || baseFunction === 'maintenance') ? (data.dept || baseFunction) : null;

    var oldRole = (state.roles || []).filter(function (r) { return r.key === s.roleKey; })[0];
    var changes = [];
    if (s.name !== name) changes.push('姓名「' + s.name + '」改為「' + name + '」');
    if ((s.email || '') !== email) changes.push('email 改為「' + email + '」');
    if (s.roleKey !== role.key) changes.push('角色「' + (oldRole ? oldRole.name : s.roleKey) + '」改為「' + role.name + '」');
    if ((s.dept || null) !== dept) {
      changes.push('部門／職能「' + (s.dept ? deptLabel(s.dept) : '無') + '」改為「' + (dept ? deptLabel(dept) : '無') + '」');
    }
    if (!changes.length) return { ok: true, staff: clone(s), noop: true };

    mutateState(function (st) {
      var it = st.staff.filter(function (x) { return x.id === staffId; })[0];
      if (it) { it.name = name; it.email = email; it.role = baseFunction; it.roleKey = role.key; it.dept = dept; }
    }, actorName, '編輯員工「' + name + '」：' + changes.join('；'));

    return { ok: true, staff: clone(s) };
  }

  function setStaffActive(staffId, active, actorName) {
    var state = getState();
    var s = state.staff.filter(function (x) { return x.id === staffId; })[0];
    if (!s) return { ok: false, code: 'not_found', message: '找不到這位員工' };
    var name = s.name;
    mutateState(function (st) {
      var it = st.staff.filter(function (x) { return x.id === staffId; })[0];
      if (it) it.active = active;
    }, actorName, (active ? '啟用' : '停用') + '員工「' + name + '」');
    return { ok: true };
  }

  // 模擬點了 email 裡的 activation link：不是真的驗證 token，示範用直接標記開通。
  function activateStaff(staffId, actorName) {
    var state = getState();
    var s = state.staff.filter(function (x) { return x.id === staffId; })[0];
    if (!s) return { ok: false, code: 'not_found', message: '找不到這位員工' };
    if (s.active !== false) return { ok: false, code: 'noop', message: '這位員工已經是啟用狀態' };
    mutateState(function (st) {
      var it = st.staff.filter(function (x) { return x.id === staffId; })[0];
      if (it) {
        it.active = true;
        it.invite = it.invite || {};
        it.invite.status = 'activated';
        it.invite.activatedAt = now();
      }
    }, actorName, '開通員工「' + s.name + '」的帳號');
    return { ok: true };
  }

  function resendInvite(staffId, actorName) {
    var state = getState();
    var s = state.staff.filter(function (x) { return x.id === staffId; })[0];
    if (!s) return { ok: false, code: 'not_found', message: '找不到這位員工' };
    mutateState(function (st) {
      var it = st.staff.filter(function (x) { return x.id === staffId; })[0];
      if (it) {
        it.invite = it.invite || {};
        it.invite.token = uid('inv');
        it.invite.invitedAt = now();
      }
    }, actorName, '重新寄送「' + s.name + '」的開通連結');
    return { ok: true };
  }

  // 使用中＝目前有在職（active）員工掛著這個角色；用來擋「停用使用中角色」。
  function roleInUse(key) {
    var state = getState();
    return (state.staff || []).some(function (s) { return s.roleKey === key && s.active !== false; });
  }

  function addRole(data, actorName) {
    var name = (data.name || '').trim();
    if (!name) return { ok: false, code: 'invalid', message: '角色名稱不能空白' };
    var baseFunction = data.baseFunction;
    if (!ROLE_LABELS[baseFunction]) return { ok: false, code: 'invalid', message: '請選擇這個角色比照哪種工作類型' };
    var state = getState();
    if ((state.roles || []).some(function (r) { return r.name === name; })) {
      return { ok: false, code: 'duplicate', message: '已經有同名角色' };
    }
    var role = {
      key: 'role_' + uid('r').slice(2), name: name, baseFunction: baseFunction,
      builtin: false, active: true,
      permissions: data.permissions ? clone(data.permissions) : blankPermissions(false),
      createdAt: now()
    };
    mutateState(function (st) { st.roles.push(role); }, actorName, '新增角色「' + name + '」');
    return { ok: true, role: role };
  }

  function renameRole(key, name, actorName) {
    var v = (name || '').trim();
    if (!v) return { ok: false, code: 'invalid', message: '角色名稱不能空白' };
    var state = getState();
    var r = (state.roles || []).filter(function (x) { return x.key === key; })[0];
    if (!r) return { ok: false, code: 'not_found', message: '找不到這個角色' };
    if ((state.roles || []).some(function (x) { return x.key !== key && x.name === v; })) {
      return { ok: false, code: 'duplicate', message: '已經有同名角色' };
    }
    var old = r.name;
    mutateState(function (st) {
      var it = st.roles.filter(function (x) { return x.key === key; })[0];
      if (it) it.name = v;
    }, actorName, '角色「' + old + '」改名為「' + v + '」');
    return { ok: true };
  }

  function setRolePermission(key, permKey, value, actorName) {
    var state = getState();
    var r = (state.roles || []).filter(function (x) { return x.key === key; })[0];
    if (!r) return { ok: false, code: 'not_found', message: '找不到這個角色' };
    if (r.key === 'administrator') return { ok: false, code: 'locked', message: 'Administrator 權限固定為全開，不可調整' };
    mutateState(function (st) {
      var it = st.roles.filter(function (x) { return x.key === key; })[0];
      if (it) { it.permissions = it.permissions || {}; it.permissions[permKey] = value; }
    }, actorName, '角色「' + r.name + '」權限「' + (PERMISSION_LABELS[permKey] || permKey) + '」改為' + (value ? '開' : '關'));
    return { ok: true };
  }

  function setRoleActive(key, active, actorName) {
    var state = getState();
    var r = (state.roles || []).filter(function (x) { return x.key === key; })[0];
    if (!r) return { ok: false, code: 'not_found', message: '找不到這個角色' };
    if (r.key === 'administrator') return { ok: false, code: 'locked', message: 'Administrator 是系統內建角色，不可停用' };
    if (active === false && roleInUse(key)) {
      return { ok: false, code: 'in_use', message: '這個角色目前還有在職員工使用中，無法停用' };
    }
    mutateState(function (st) {
      var it = st.roles.filter(function (x) { return x.key === key; })[0];
      if (it) it.active = active;
    }, actorName, (active ? '啟用' : '停用') + '角色「' + r.name + '」');
    return { ok: true };
  }

  // staffId 允許傳入虛擬身分 '__owner__'（settings.html 的「租戶管理者」預設身分，
  // 不綁定任何員工、永遠視為全權限）——引擎本身只認真正的員工與角色，
  // 這個特例交給呼叫端判斷，保持 hasPermission 對一般員工查詢的語意單純。
  function hasPermission(staffId, permKey) {
    if (!staffId) return false;
    var state = getState();
    var s = (state.staff || []).filter(function (x) { return x.id === staffId; })[0];
    if (!s) return false;
    var role = (state.roles || []).filter(function (r) { return r.key === s.roleKey; })[0];
    if (!role || role.active === false) return false;
    return !!(role.permissions && role.permissions[permKey]);
  }

  function addDepartment(name, actorName) {
    var v = (name || '').trim();
    if (!v) return { ok: false, code: 'invalid', message: '部門名稱不能空白' };
    var state = getState();
    if ((state.settings.departments || []).some(function (d) { return d.name === v; })) {
      return { ok: false, code: 'duplicate', message: '已經有同名部門' };
    }
    var dept = { key: 'dept_' + uid('d').slice(2), name: v, active: true };
    updateSettings(function (s) { s.departments.push(dept); }, actorName, '新增部門「' + v + '」');
    return { ok: true, department: dept };
  }

  function renameDepartment(key, name, actorName) {
    var v = (name || '').trim();
    if (!v) return { ok: false, code: 'invalid', message: '部門名稱不能空白' };
    var state = getState();
    var d = (state.settings.departments || []).filter(function (x) { return x.key === key; })[0];
    if (!d) return { ok: false, code: 'not_found', message: '找不到這個部門' };
    var oldName = d.name;
    updateSettings(function (s) {
      var it = s.departments.filter(function (x) { return x.key === key; })[0];
      if (it) it.name = v;
    }, actorName, '部門「' + oldName + '」改名為「' + v + '」');
    return { ok: true };
  }

  function setDepartmentActive(key, active, actorName) {
    var state = getState();
    var d = (state.settings.departments || []).filter(function (x) { return x.key === key; })[0];
    if (!d) return { ok: false, code: 'not_found', message: '找不到這個部門' };
    var name = d.name;
    updateSettings(function (s) {
      var it = s.departments.filter(function (x) { return x.key === key; })[0];
      if (it) it.active = active;
    }, actorName, (active ? '啟用' : '停用') + '部門「' + name + '」');
    return { ok: true };
  }

  // 自動化規則的觸發源字彙與排程重複字彙（v1）。weekday 用 ISO 慣例 1–7（週一＝1），
  // 不用 JS getDay() 的週日＝0——每週冪等鍵以 ISO 週為界，字彙若跟著 getDay() 走，
  // 週日會被算進下一週，同一條規則一週會建兩筆。
  var AUTOMATION_TRIGGERS = ['reservation', 'schedule'];
  var AUTOMATION_TRIGGER_LABELS = {
    reservation: '訂房資料進來',
    schedule: '排程時間到'
  };
  var SCHEDULE_REPEATS = ['daily', 'weekly'];
  var WEEKDAY_LABELS = { 1: '週一', 2: '週二', 3: '週三', 4: '週四', 5: '週五', 6: '週六', 7: '週日' };

  // 規則排程描述的人話版本（「每日 08:00」「每週三 14:30」），設定頁與任務留痕共用
  // ——兩邊各自拼字串遲早長出不一致的講法。
  function scheduleLabelOf(sch) {
    if (!sch) return '';
    if (sch.repeat === 'weekly') return '每' + (WEEKDAY_LABELS[sch.weekday] || '週?') + ' ' + sch.time;
    return '每日 ' + sch.time;
  }

  // 要保證的是：存進 automationRules 的每條規則都帶著它的觸發源用得到的合法條件
  // （訂房觸發有屬性、排程觸發有合法時點），不論這條規則是新增還是編輯進來的。
  // 排程掃描對「沒有合法時點的規則」只能整條跳過，一旦編輯那條路放進半套資料，
  // 使用者看到的會是「規則明明在清單上卻永遠不觸發」這種查不出原因的故障。
  function validateAutomationRule(state, trigger, data) {
    var title = (data.title || '').trim();
    if (!title) return { ok: false, code: 'invalid', message: '請填工單名稱' };
    if (!TYPE_LABELS[data.type]) return { ok: false, code: 'invalid', message: '請選擇任務類型' };
    if (!(state.settings.departments || []).some(function (d) { return d.key === data.dept; })) {
      return { ok: false, code: 'invalid', message: '請選擇部門' };
    }
    var dueAfterMin = null;
    if (data.dueAfterMin !== undefined && data.dueAfterMin !== null && String(data.dueAfterMin).trim() !== '') {
      dueAfterMin = parseFloat(data.dueAfterMin);
      if (!(dueAfterMin > 0)) return { ok: false, code: 'invalid', message: '到期時間要是大於 0 的分鐘數（留空代表不設到期）' };
    }
    var out = { ok: true, title: title, dept: data.dept, type: data.type, dueAfterMin: dueAfterMin, attribute: null, schedule: null };
    if (trigger === 'reservation') {
      var attribute = (data.attribute || '').trim();
      if (!attribute) return { ok: false, code: 'invalid', message: '請填訂房屬性' };
      out.attribute = attribute;
    } else if (trigger === 'schedule') {
      var sch = data.schedule || {};
      if (SCHEDULE_REPEATS.indexOf(sch.repeat) === -1) return { ok: false, code: 'invalid', message: '請選擇重複方式（每日或每週）' };
      if (!isValidTime(sch.time)) return { ok: false, code: 'invalid', message: '時間格式要是 HH:MM' };
      var weekday = null;
      if (sch.repeat === 'weekly') {
        weekday = parseInt(sch.weekday, 10);
        if (!(weekday >= 1 && weekday <= 7)) return { ok: false, code: 'invalid', message: '每週規則要選星期幾' };
      }
      out.schedule = { repeat: sch.repeat, weekday: weekday, time: sch.time, roomNo: String(sch.roomNo || '').trim() };
    } else {
      return { ok: false, code: 'invalid', message: '未知的觸發源' };
    }
    return out;
  }

  // 自動化規則 CRUD：跟部門/角色一樣即存即生效、走 settings_changed 事件；
  // 不做「使用中無法刪除」這種保護——規則不是員工也不是部門，刪掉只影響之後的
  // 觸發比對，已經開出去的單不受影響（dedupeKey/ruleKey 只是留痕，刪了規則
  // 不會回頭動那些已建立的任務）。內建四條（準備工單）也一樣可改可停可刪。
  function addAutomationRule(data, actorName) {
    var trigger = data.trigger || 'reservation';
    var state = getState();
    var v = validateAutomationRule(state, trigger, data);
    if (!v.ok) return v;
    var rule = {
      key: 'auto_' + uid('r').slice(2), trigger: trigger,
      attribute: v.attribute, schedule: v.schedule,
      title: v.title, dept: v.dept, type: v.type,
      dueAfterMin: v.dueAfterMin, enabled: true, builtin: false, createdAt: now()
    };
    var what = trigger === 'reservation' ? ('訂房屬性「' + v.attribute + '」') : scheduleLabelOf(v.schedule);
    updateSettings(function (s) {
      s.automationRules = s.automationRules || [];
      s.automationRules.push(rule);
    }, actorName, '新增自動化規則「' + what + ' → ' + v.title + '」');
    return { ok: true, rule: rule };
  }

  // 觸發源不開放改：排程規則的冪等鍵不含房號、訂房規則的含房號，改觸發源等於
  // 換一條冪等語意不同的規則，舊鍵新鍵對不上，可能在同一天再建一筆。要換就刪掉重建。
  function updateAutomationRule(key, data, actorName) {
    var state = getState();
    var rule = (state.settings.automationRules || []).filter(function (r) { return r.key === key; })[0];
    if (!rule) return { ok: false, code: 'not_found', message: '找不到這條規則' };
    var v = validateAutomationRule(state, rule.trigger, data);
    if (!v.ok) return v;
    var what = rule.trigger === 'reservation' ? ('訂房屬性「' + v.attribute + '」') : scheduleLabelOf(v.schedule);
    updateSettings(function (s) {
      var it = (s.automationRules || []).filter(function (r) { return r.key === key; })[0];
      if (!it) return;
      it.title = v.title; it.dept = v.dept; it.type = v.type; it.dueAfterMin = v.dueAfterMin;
      if (rule.trigger === 'reservation') it.attribute = v.attribute;
      else it.schedule = v.schedule;
    }, actorName, '更新自動化規則「' + what + ' → ' + v.title + '」');
    return { ok: true };
  }

  function setAutomationRuleActive(key, active, actorName) {
    var state = getState();
    var rule = (state.settings.automationRules || []).filter(function (r) { return r.key === key; })[0];
    if (!rule) return { ok: false, code: 'not_found', message: '找不到這條規則' };
    updateSettings(function (s) {
      var it = (s.automationRules || []).filter(function (r) { return r.key === key; })[0];
      if (it) it.enabled = active;
    }, actorName, (active ? '啟用' : '停用') + '自動化規則「' + rule.title + '」');
    return { ok: true };
  }

  function deleteAutomationRule(key, actorName) {
    var state = getState();
    var rule = (state.settings.automationRules || []).filter(function (r) { return r.key === key; })[0];
    if (!rule) return { ok: false, code: 'not_found', message: '找不到這條規則' };
    updateSettings(function (s) {
      s.automationRules = (s.automationRules || []).filter(function (r) { return r.key !== key; });
    }, actorName, '刪除自動化規則「' + rule.title + '」');
    return { ok: true };
  }

  function genQrToken(roomNo) {
    return 'qr' + roomNo + '_' + Math.random().toString(36).slice(2, 8);
  }

  function addRoom(roomNo, actorName) {
    var v = String(roomNo || '').trim();
    if (!/^\d{3,4}$/.test(v)) return { ok: false, code: 'invalid', message: '房號格式不對（3~4 位數字）' };
    var state = getState();
    if ((state.rooms || []).some(function (r) { return r.roomNo === v; })) {
      return { ok: false, code: 'duplicate', message: '這個房號已經存在' };
    }
    var room = { roomNo: v, active: true, qrToken: genQrToken(v), qrRotatedAt: null };
    mutateState(function (st) { st.rooms.push(room); }, actorName, '新增房間「' + v + '」');
    return { ok: true, room: room };
  }

  function setRoomActive(roomNo, active, actorName) {
    var state = getState();
    var r = (state.rooms || []).filter(function (x) { return x.roomNo === roomNo; })[0];
    if (!r) return { ok: false, code: 'not_found', message: '找不到這間房' };
    mutateState(function (st) {
      var it = st.rooms.filter(function (x) { return x.roomNo === roomNo; })[0];
      if (it) it.active = active;
    }, actorName, (active ? '啟用' : '停用') + '房間「' + roomNo + '」');
    return { ok: true };
  }

  // 獨立事件字彙（不是 settings_changed）：舊 QR 作廢是一個業務事實，不是單純設定值
  // 變更；沒有 guest session 概念可以連坐撤銷登出，這筆事件＋guest.html 的 token
  // 比對（比對不到現在的 qrToken 就擋下）就是等價的作廢證明。
  function rotateRoomQr(roomNo, actorName) {
    var state = getState();
    var room = (state.rooms || []).filter(function (r) { return r.roomNo === roomNo; })[0];
    if (!room) return { ok: false, code: 'not_found', message: '找不到這間房' };
    var oldToken = room.qrToken;
    room.qrToken = genQrToken(roomNo);
    room.qrRotatedAt = now();
    addEvent(state, roomNo, 'qr_rotated', actorName || '租戶設定', { roomNo: roomNo, oldToken: oldToken, newToken: room.qrToken });
    save(state);
    return { ok: true, room: clone(room) };
  }

  function rotateAllRoomQr(actorName) {
    var state = getState();
    var rooms = state.rooms || [];
    if (!rooms.length) return { ok: false, code: 'empty', message: '目前沒有房間可重產' };
    rooms.forEach(function (room) {
      var oldToken = room.qrToken;
      room.qrToken = genQrToken(room.roomNo);
      room.qrRotatedAt = now();
      addEvent(state, room.roomNo, 'qr_rotated', actorName || '租戶設定', { roomNo: room.roomNo, oldToken: oldToken, newToken: room.qrToken });
    });
    save(state);
    return { ok: true, count: rooms.length };
  }

  function isValidTime(v) {
    return /^([01]\d|2[0-3]):[0-5]\d$/.test(v || '');
  }

  function addShift(data, actorName) {
    var name = (data.name || '').trim();
    if (!name) return { ok: false, code: 'invalid', message: '班次名稱不能空白' };
    if (!isValidTime(data.start) || !isValidTime(data.end)) {
      return { ok: false, code: 'invalid', message: '時間格式要是 HH:MM' };
    }
    var shift = { key: 'shift_' + Date.now().toString(36), name: name, start: data.start, end: data.end };
    updateSettings(function (s) { s.shifts.push(shift); }, actorName, '新增班次「' + name + '」');
    return { ok: true, shift: shift };
  }

  function updateShift(key, patch, actorName) {
    var state = getState();
    var sh = (state.settings.shifts || []).filter(function (x) { return x.key === key; })[0];
    if (!sh) return { ok: false, code: 'not_found', message: '找不到這個班次' };
    var name = (patch.name || sh.name || '').trim();
    var start = patch.start || sh.start;
    var end = patch.end || sh.end;
    if (!name) return { ok: false, code: 'invalid', message: '班次名稱不能空白' };
    if (!isValidTime(start) || !isValidTime(end)) return { ok: false, code: 'invalid', message: '時間格式要是 HH:MM' };
    updateSettings(function (s) {
      var it = s.shifts.filter(function (x) { return x.key === key; })[0];
      if (it) { it.name = name; it.start = start; it.end = end; }
    }, actorName, '更新班次「' + name + '」（' + start + '–' + end + '）');
    return { ok: true };
  }

  function deleteShift(key, actorName) {
    var state = getState();
    if ((state.settings.shifts || []).length <= 1) {
      return { ok: false, code: 'last_one', message: '至少要留一個班次' };
    }
    var sh = (state.settings.shifts || []).filter(function (x) { return x.key === key; })[0];
    if (!sh) return { ok: false, code: 'not_found', message: '找不到這個班次' };
    updateSettings(function (s) {
      s.shifts = s.shifts.filter(function (x) { return x.key !== key; });
    }, actorName, '刪除班次「' + sh.name + '」');
    return { ok: true };
  }

  function minutesOfDay(hhmm) {
    var p = String(hhmm || '0:0').split(':');
    return (parseInt(p[0], 10) || 0) * 60 + (parseInt(p[1], 10) || 0);
  }

  // 租戶自己調時段時可能留下空隙或重疊，POC 不想為了校驗這個擋住操作；真落在
  // 空隙裡就退回第一個班次顯示，讓畫面永遠有值，不會開天窗。
  function getCurrentShift(date) {
    var shifts = getSettings().shifts || [];
    if (!shifts.length) return null;
    var d = date || new Date();
    var nowMin = d.getHours() * 60 + d.getMinutes();
    var hit = shifts.filter(function (s) {
      var s0 = minutesOfDay(s.start), s1 = minutesOfDay(s.end);
      if (s0 === s1) return true;
      if (s0 < s1) return nowMin >= s0 && nowMin < s1;
      return nowMin >= s0 || nowMin < s1;
    })[0];
    return hit || shifts[0];
  }

  // 逾時升級的收件角色字彙。duty_manager（值班經理）在這個 POC 沒有獨立頁面，
  // 映射成「櫃檯看板＋老闆頁並列」——櫃檯是全天有人盯的實體值班台、老闆頁是
  // 既有的「出事才會響」推播面，兩邊並列最接近「推播值班經理手機」的真實語意。
  var ESCALATION_RECIPIENTS = ['assignee_or_pool', 'manager', 'desk', 'owner', 'duty_manager'];
  var ESCALATION_RECIPIENT_LABELS = {
    assignee_or_pool: '接單人／部門待接池',
    manager: '部門主管',
    desk: '櫃檯',
    owner: '老闆',
    duty_manager: '值班經理（POC 映射：櫃檯＋老闆並列）'
  };

  // 收件角色 → 要在哪些頁面出現提醒。各頁一律經 escalationsForPage 查這張表，
  // 映射只寫在這一處——四個頁面各自解讀「duty_manager 是誰」遲早長出不一致。
  var ESCALATION_RECIPIENT_PAGES = {
    assignee_or_pool: ['housekeeping'],
    manager: ['manager'],
    desk: ['desk'],
    owner: ['owner'],
    duty_manager: ['desk', 'owner']
  };

  // ISO 週鍵（例如 2026-W44）：每週排程規則的冪等鍵用它當「這一週」的名字。
  // 用 ISO 定義（週一起算、含當年第一個週四的那週是第 1 週）而不是自己數天，
  // 是因為跨年那幾天用「日期除以七」這類土法會把同一週切成兩個鍵，規則在年底
  // 一週內建出兩筆。
  function isoWeekKeyOf(date) {
    var d = new Date(date.getTime());
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7));
    var week1 = new Date(d.getFullYear(), 0, 4);
    var weekNo = 1 + Math.round(((d - week1) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7);
    return d.getFullYear() + '-W' + weekNo;
  }

  // 今天是 ISO 慣例的星期幾（週一＝1 … 週日＝7），跟規則 schedule.weekday 同一套值。
  function isoWeekdayOf(date) {
    return ((date.getDay() + 6) % 7) + 1;
  }

  /**
   * 排程觸發掃描：trigger=schedule 的規則到了設定時點就自動建任務，進對應部門
   * 待接池。判定用「區間（現在 ≥ 時點）＋冪等鍵」而不是「現在 == 時點那一分鐘」
   * ——分頁可能在時點當下根本沒開著，等號判定會整天漏掉；區間判定配合冪等鍵，
   * 晚開的分頁會在下一次掃描補建那一筆，且只補一筆（對應正式版 worker 的
   * 「停機補觸發」語意）。
   *
   * 冪等鍵＝規則｜當地日期（每日）或 規則｜ISO 週（每週），存在任務的
   * autoDedupeKey 上：重新整理、關掉重開、多分頁同時掃，查到同鍵任務就不再建。
   * 每週規則在本 ISO 週的時點（星期幾＋HH:MM）過了才建，過了好幾天才開分頁
   * 也只補那一筆；下一個 ISO 週換鍵，重新計一次。
   */
  function scanScheduleRules() {
    var state = getState();
    var rules = (state.settings.automationRules || []).filter(function (r) {
      return r.trigger === 'schedule' && r.enabled !== false && r.schedule && isValidTime(r.schedule.time);
    });
    if (!rules.length) return { ok: true, created: 0 };
    var d = new Date();
    var nowMin = d.getHours() * 60 + d.getMinutes();
    var created = 0;
    rules.forEach(function (rule) {
      var sch = rule.schedule;
      var dedupeKey;
      if (sch.repeat === 'weekly') {
        var todayIso = isoWeekdayOf(d);
        if (todayIso < sch.weekday) return;
        if (todayIso === sch.weekday && nowMin < minutesOfDay(sch.time)) return;
        dedupeKey = rule.key + '|' + isoWeekKeyOf(d);
      } else {
        if (nowMin < minutesOfDay(sch.time)) return;
        dedupeKey = rule.key + '|' + todayKeyOf();
      }
      var already = state.tasks.some(function (t) { return t.autoDedupeKey === dedupeKey; });
      if (already) return;
      var task = {
        id: uid('task'), roomNo: sch.roomNo || '', dept: rule.dept, type: rule.type,
        state: 'pool', assigneeId: null, title: rule.title, detail: '',
        source: 'automation_schedule',
        ruleHit: scheduleLabelOf(sch) + ' → ' + deptLabel(rule.dept) + '待接池（自動化規則：排程觸發）',
        redoCount: 0, sop: rule.type === 'cleaning' ? seedSop(state) : [],
        rating: null, version: 1, createdAt: now(), updatedAt: now(),
        autoDedupeKey: dedupeKey, autoRuleKey: rule.key
      };
      if (rule.dueAfterMin > 0) task.dueAt = task.createdAt + rule.dueAfterMin * 60000;
      state.tasks.push(task);
      addEvent(state, task.id, 'created', '系統（自動化規則）', {
        source: 'automation_schedule', ruleKey: rule.key, schedule: scheduleLabelOf(sch),
        note: '自動化規則（' + rule.title + '）排程建立'
      });
      created++;
    });
    if (created) save(state);
    return { ok: true, created: created };
  }

  // 只有「還有人該動作」的狀態才升級；done/inspected/cancelled 一到就自然停，
  // 不用額外清旗標。與任務卡逾時標記（components.js）同一組狀態。
  var ESCALATABLE_STATES = ['triage', 'pool', 'accepted', 'doing'];

  function escalationConfig(settings) {
    return (settings || {}).escalation || { enabled: false, levels: [] };
  }

  /**
   * 逾時升級掃描：任務超過 SLA 後，到達某層時點（逾時後第 afterOverdueMin 分鐘）
   * 就升到那層——升級是通知行為不是新狀態，任務停在原狀態，九態一個都不加；
   * 留痕走 escalated 事件（payload 帶層級與收件角色），任務上另存 escalationLevel
   * 與 escalations 快照讓各角色頁直接渲染提醒，不用反查事件流水。
   *
   * 每層只升一次：只處理「比 task.escalationLevel 高」的層，重複掃描不會重複記。
   * 多分頁同時掃也安全——寫入是整份狀態覆寫，同一層兩個分頁搶寫的結果內容相同，
   * 不會像逐筆 append 那樣疊出兩筆重複事件。分頁關掉一段時間再開，錯過的層會在
   * 下一次掃描一次補齊（每層仍各記一筆），對應正式版「停機補觸發」的語意。
   */
  function scanEscalations() {
    var state = getState();
    var esc = escalationConfig(state.settings);
    if (esc.enabled === false || !(esc.levels || []).length) return { ok: true, escalated: 0 };
    var slaByType = state.settings.slaByType || {};
    var nowTs = now();
    var changed = 0;
    state.tasks.forEach(function (task) {
      if (ESCALATABLE_STATES.indexOf(task.state) === -1) return;
      // 逾期起點二選一：任務帶 dueAt（自動化規則設了「建立後 N 分鐘到期」）就以
      // dueAt 為準，連該類型沒設 SLA 的任務也照升——規則講好的到期時間比全類型
      // 一套的 SLA 更貼近「這一單」的承諾；兩套同時算會對同一單給出兩個互相
      // 矛盾的逾期時刻，升級與卡片標記就對不上。沒有 dueAt 的維持原樣走
      // createdAt＋SLA。
      var overdueMin;
      if (task.dueAt) {
        overdueMin = (nowTs - task.dueAt) / 60000;
      } else {
        var sla = slaByType[task.type];
        if (!(sla > 0)) return;
        overdueMin = (nowTs - task.createdAt) / 60000 - sla;
      }
      if (overdueMin < 0) return;
      (esc.levels || []).forEach(function (lv) {
        if (lv.enabled === false) return;
        if (lv.level <= (task.escalationLevel || 0)) return;
        if (overdueMin < (lv.afterOverdueMin || 0)) return;
        // 第一層收件人固定是接單人／部門待接池：「這單過時限了」第一個該知道的
        // 人沒有懸念，開放改只會做出「逾時卻沒人先問現場」的設定。
        var recipient = lv.level === 1 ? 'assignee_or_pool' : (lv.recipient || 'manager');
        task.escalationLevel = lv.level;
        task.escalations = task.escalations || [];
        task.escalations.push({ level: lv.level, recipient: recipient, ts: nowTs });
        addEvent(state, task.id, 'escalated', '系統（逾時升級）', {
          level: lv.level,
          recipient: recipient,
          recipientLabel: ESCALATION_RECIPIENT_LABELS[recipient] || recipient,
          overdueMin: Math.round(overdueMin * 10) / 10,
          roomNo: task.roomNo
        });
        changed++;
      });
    });
    if (changed) save(state);
    return { ok: true, escalated: changed };
  }

  // 某個頁面現在該顯示哪些升級提醒：仍在可升級狀態的任務，取其升級快照裡
  // 收件對象映射到這個頁面的那幾層。同一任務多層命中同頁時只回最高層——
  // 頁面提醒一單一行就夠，三層各一行只會把真正新的提醒擠出視線。
  function escalationsForPage(state, page) {
    var out = [];
    (state.tasks || []).forEach(function (task) {
      if (ESCALATABLE_STATES.indexOf(task.state) === -1) return;
      var hits = (task.escalations || []).filter(function (e) {
        return (ESCALATION_RECIPIENT_PAGES[e.recipient] || []).indexOf(page) !== -1;
      });
      if (!hits.length) return;
      var top = hits[hits.length - 1];
      out.push({ task: task, level: top.level, recipient: top.recipient, ts: top.ts });
    });
    return out.sort(function (a, b) { return b.level - a.level || a.task.createdAt - b.task.createdAt; });
  }

  // 掃描間隔 5 秒：比各頁 20 秒重繪密，層距調到 0.5 分鐘這種驗收值也抓得準；
  // 沒有變更時掃描不寫入、不觸發任何重繪，閒置成本只有兩次 localStorage 讀取。
  // 排程建單排在升級掃描前面——同一輪先把時間到的任務生出來，帶 dueAt 的新任務
  // 下一輪就能進入逾期判定，不用多等一個週期。
  function scanTimers() {
    scanScheduleRules();
    scanEscalations();
  }
  setInterval(scanTimers, 5000);
  setTimeout(scanTimers, 800);

  // 種子事件被整批換掉是既有語意（重置＝回到 demo 的起始時間點），但「重置」
  // 這個動作本身也要留痕，不然流水帳會憑空出現一批過去的事件、看不出是誰在什麼
  // 時候按了重置——所以種子狀態寫入後再補記一筆，走跟 updateSettings 一樣的
  // settings_changed 字彙，save() 順便處理 rev/broadcast/notifyLocal。
  function resetAll() {
    var seeded = global.TMS_SEED.buildSeedState();
    addEvent(seeded, 'settings', 'settings_changed', '租戶設定', { summary: '重置還原預設' });
    save(seeded);
    return seeded;
  }

  global.TMS_ENGINE = {
    STATES: STATES,
    STATE_LABELS: STATE_LABELS,
    DEPT_LABELS: DEPT_LABELS,
    ROLE_LABELS: ROLE_LABELS,
    TYPE_LABELS: TYPE_LABELS,
    INSPECTION_ITEMS: INSPECTION_ITEMS,
    CHECKLIST_RULES: CHECKLIST_RULES,
    CANNED_ANSWERS: CANNED_ANSWERS,
    CLEANING_STANDARD: CLEANING_STANDARD,
    ROOM_STATES: ROOM_STATES,
    ROOM_STATE_LABELS: ROOM_STATE_LABELS,
    ROOM_FLAGS: ROOM_FLAGS,
    ROOM_FLAG_LABELS: ROOM_FLAG_LABELS,
    scheduleInclusionOf: scheduleInclusionOf,
    defaultRoomStateOf: defaultRoomStateOf,
    estimateCleaningMinutes: estimateCleaningMinutes,
    workloadUnitLabel: workloadUnitLabel,
    PERMISSION_GROUPS: PERMISSION_GROUPS,
    PERMISSION_LABELS: PERMISSION_LABELS,
    ROLE_TEMPLATES: ROLE_TEMPLATES,
    ESCALATION_RECIPIENTS: ESCALATION_RECIPIENTS,
    ESCALATION_RECIPIENT_LABELS: ESCALATION_RECIPIENT_LABELS,
    scanEscalations: scanEscalations,
    escalationsForPage: escalationsForPage,
    AUTOMATION_TRIGGERS: AUTOMATION_TRIGGERS,
    AUTOMATION_TRIGGER_LABELS: AUTOMATION_TRIGGER_LABELS,
    SCHEDULE_REPEATS: SCHEDULE_REPEATS,
    WEEKDAY_LABELS: WEEKDAY_LABELS,
    scheduleLabelOf: scheduleLabelOf,
    scanScheduleRules: scanScheduleRules,
    PMS_MODE_LABELS: PMS_MODE_LABELS,
    pmsSettingsOf: pmsSettingsOf,
    pmsProviderOf: pmsProviderOf,
    pmsWritebackEnabled: pmsWritebackEnabled,
    testPmsConnection: testPmsConnection,

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
    deptLabel: deptLabel,
    floorOf: floorOf,
    findTask: findTask,
    seedSop: seedSop,

    addStaff: addStaff,
    updateStaff: updateStaff,
    setStaffActive: setStaffActive,
    activateStaff: activateStaff,
    resendInvite: resendInvite,
    addRole: addRole,
    renameRole: renameRole,
    setRolePermission: setRolePermission,
    setRoleActive: setRoleActive,
    roleInUse: roleInUse,
    hasPermission: hasPermission,
    addDepartment: addDepartment,
    renameDepartment: renameDepartment,
    setDepartmentActive: setDepartmentActive,
    addAutomationRule: addAutomationRule,
    updateAutomationRule: updateAutomationRule,
    setAutomationRuleActive: setAutomationRuleActive,
    deleteAutomationRule: deleteAutomationRule,
    addRoom: addRoom,
    setRoomActive: setRoomActive,
    rotateRoomQr: rotateRoomQr,
    rotateAllRoomQr: rotateAllRoomQr,
    addShift: addShift,
    updateShift: updateShift,
    deleteShift: deleteShift,
    getCurrentShift: getCurrentShift,

    guestSubmitChecklist: guestSubmitChecklist,
    guestSubmitFreetext: guestSubmitFreetext,
    guestWithdraw: guestWithdraw,
    guestRate: guestRate,

    deskTriage: deskTriage,
    deskAnswer: deskAnswer,
    sendNotice: sendNotice,
    readNotice: readNotice,
    reportToOwner: reportToOwner,
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
