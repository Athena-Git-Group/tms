/**
 * TMS POC — 種子資料
 *
 * 房號沿用 docs/05-dispatch-sandbox.html 的慣例（三~四位數、301-305/502 這類號碼），
 * 讓同一批人看兩份 demo 時房號對得起來，不會覺得是兩個不相干的系統。
 */
(function (global) {
  'use strict';

  var STAFF = [
    { id: 'hk1', name: '小美', role: 'housekeeping', dept: 'housekeeping' },
    { id: 'hk2', name: '阿芳', role: 'housekeeping', dept: 'housekeeping' },
    { id: 'mt1', name: '阿強', role: 'maintenance', dept: 'maintenance' },
    { id: 'dk1', name: '小陳', role: 'desk', dept: 'desk' },
    { id: 'mg1', name: '王主管', role: 'manager', dept: null }
  ];

  var HOTEL = {
    name: '安棲商旅',
    breakfast: '早餐供應時間 06:30–10:00，地點在二樓咖啡廳',
    wifiSsid: 'ANQI-GUEST',
    wifiPassword: 'anqi2026'
  };

  // 模擬 PMS adapter 回傳的「今日房態」。刻意用種子任務沒碰過的房號，
  // 排房 demo 才有房可分；301-305/502-503 已在種子裡有今日打掃單，匯入會被對帳跳過。
  var PMS_TODAY = [
    { roomNo: '306', kind: '退房清' },
    { roomNo: '307', kind: '續住清' },
    { roomNo: '308', kind: '退房清' },
    { roomNo: '505', kind: '夜床' },
    { roomNo: '506', kind: '退房清' },
    { roomNo: '601', kind: '續住清' },
    { roomNo: '602', kind: '空房巡' }
  ];

  function staffName(staffId) {
    var s = STAFF.filter(function (x) { return x.id === staffId; })[0];
    return s ? s.name : staffId;
  }

  function sopHalfChecked() {
    return [
      { key: 'bed', label: '床鋪', checked: true },
      { key: 'bathroom', label: '浴室', checked: true },
      { key: 'amenity', label: '備品', checked: false },
      { key: 'qr', label: 'QR 貼紙', checked: false }
    ];
  }

  function sopEmpty() {
    return [
      { key: 'bed', label: '床鋪', checked: false },
      { key: 'bathroom', label: '浴室', checked: false },
      { key: 'amenity', label: '備品', checked: false },
      { key: 'qr', label: 'QR 貼紙', checked: false }
    ];
  }

  // 租戶設定的出廠預設。引擎在讀到「沒有 settings 的舊狀態」時也會用這份補齊，
  // 所以改預設值只要改這裡一處。
  function defaultSettings() {
    return {
      hotelName: HOTEL.name,
      infoItems: [
        { id: 'info_breakfast', icon: '🥐', title: '早餐', content: HOTEL.breakfast, enabled: true },
        { id: 'info_wifi', icon: '📶', title: 'Wi-Fi', content: 'SSID「' + HOTEL.wifiSsid + '」密碼「' + HOTEL.wifiPassword + '」', enabled: true }
      ],
      menuItems: [
        { key: 'towel', enabled: true },
        { key: 'extra_bed', enabled: true },
        { key: 'toiletries', enabled: true },
        { key: 'ac_repair', enabled: true },
        { key: 'plumbing', enabled: true }
      ],
      flow: {
        requireInspection: true,
        freetextNeedsTriage: true,
        allowSelfInspect: false
      },
      cleaningStandard: { '退房清': 30, '續住清': 20, '夜床': 10, '空房巡': 5 },
      shiftCapacityMin: 240,
      // 打掃刻意不掛 SLA（排房計畫管它的節奏）；種子任務建立在 3 小時前，
      // 若打掃也掛時限，一重置滿版「逾時」會蓋掉真正該被看到的臨時需求逾時。
      slaByType: { amenity: 15, repair: 60, cleaning: null, other: 30 },
      customMenuItems: [],
      inspectionItems: [
        { key: 'bed', label: '床鋪' },
        { key: 'bathroom', label: '浴室' },
        { key: 'amenity', label: '備品' },
        { key: 'qr', label: 'QR 貼紙' }
      ],
      autoAssignStrategy: 'floor',
      ownerAlertMaxStars: 2
    };
  }

  // 班前排房是唯一「發佈即 accepted」的例外（規格：唯一不進池子的建單路徑），
  // 其餘任務一律先進 pool 等人認領。
  function buildSeedState() {
    var t0 = Date.now();
    var H = 60 * 60 * 1000;

    var tasks = [
      {
        id: 'task_seed_301', roomNo: '301', dept: 'housekeeping', type: 'cleaning',
        state: 'accepted', assigneeId: 'hk1', title: '退房清', detail: '',
        source: 'seed_schedule', ruleHit: null, redoCount: 0, sop: sopEmpty(),
        rating: null, version: 1, createdAt: t0 - 3 * H, updatedAt: t0 - 3 * H
      },
      {
        id: 'task_seed_302', roomNo: '302', dept: 'housekeeping', type: 'cleaning',
        state: 'accepted', assigneeId: 'hk2', title: '退房清', detail: '',
        source: 'seed_schedule', ruleHit: null, redoCount: 0, sop: sopEmpty(),
        rating: null, version: 1, createdAt: t0 - 3 * H, updatedAt: t0 - 3 * H
      },
      {
        id: 'task_seed_303', roomNo: '303', dept: 'housekeeping', type: 'cleaning',
        state: 'accepted', assigneeId: 'hk1', title: '續住清', detail: '',
        source: 'seed_schedule', ruleHit: null, redoCount: 0, sop: sopEmpty(),
        rating: null, version: 1, createdAt: t0 - 3 * H, updatedAt: t0 - 3 * H
      },
      {
        id: 'task_seed_305', roomNo: '305', dept: 'housekeeping', type: 'cleaning',
        state: 'accepted', assigneeId: 'hk2', title: '夜床', detail: '',
        source: 'seed_schedule', ruleHit: null, redoCount: 0, sop: sopEmpty(),
        rating: null, version: 1, createdAt: t0 - 3 * H, updatedAt: t0 - 3 * H
      },
      {
        id: 'task_seed_502_doing', roomNo: '502', dept: 'housekeeping', type: 'cleaning',
        state: 'doing', assigneeId: 'hk1', title: '退房清', detail: '',
        source: 'seed_schedule', ruleHit: null, redoCount: 0, sop: sopHalfChecked(),
        rating: null, version: 2, createdAt: t0 - 3 * H, updatedAt: t0 - 0.5 * H
      },
      {
        id: 'task_seed_503_done', roomNo: '503', dept: 'housekeeping', type: 'cleaning',
        state: 'done', assigneeId: 'hk2', title: '退房清', detail: '',
        source: 'seed_schedule', ruleHit: null, redoCount: 0,
        sop: [
          { key: 'bed', label: '床鋪', checked: true },
          { key: 'bathroom', label: '浴室', checked: true },
          { key: 'amenity', label: '備品', checked: true },
          { key: 'qr', label: 'QR 貼紙', checked: true }
        ],
        rating: null, version: 3, createdAt: t0 - 3 * H, updatedAt: t0 - 0.2 * H
      }
    ];

    var events = [];
    tasks.forEach(function (t) {
      events.push({ id: 'ev_seed_' + t.id + '_created', taskId: t.id, type: 'created', actor: '系統排房', ts: t.createdAt, payload: { source: 'seed_schedule' } });
      events.push({ id: 'ev_seed_' + t.id + '_accepted', taskId: t.id, type: 'claim', actor: staffName(t.assigneeId), ts: t.createdAt + 1000, payload: { from: 'pool', to: 'accepted', note: '班前排房發佈即指派' } });
      if (t.state === 'doing' || t.state === 'done') {
        events.push({ id: 'ev_seed_' + t.id + '_start', taskId: t.id, type: 'start', actor: staffName(t.assigneeId), ts: t.updatedAt - 1800000, payload: { from: 'accepted', to: 'doing' } });
      }
      if (t.state === 'done') {
        events.push({ id: 'ev_seed_' + t.id + '_complete', taskId: t.id, type: 'complete', actor: staffName(t.assigneeId), ts: t.updatedAt, payload: { from: 'doing', to: 'done' } });
      }
    });

    return {
      rev: 0,
      updatedAt: t0,
      hotel: HOTEL,
      settings: defaultSettings(),
      schedule: { draft: [], source: null, importedAt: null },
      staff: STAFF,
      tasks: tasks,
      events: events,
      notices: [
        { id: 'ntc_seed_1', message: '今日 14:00 起 3 樓電梯保養，請改走安全梯。', actor: '小陳', ts: t0 - 2 * H, readBy: ['小美'] }
      ],
      lostFound: [
        { id: 'lf_seed_1', roomNo: '210', item: '藍色行動電源', status: 'held', foundBy: '小美', ts: t0 - 5 * H }
      ]
    };
  }

  global.TMS_SEED = {
    STAFF: STAFF,
    HOTEL: HOTEL,
    PMS_TODAY: PMS_TODAY,
    defaultSettings: defaultSettings,
    buildSeedState: buildSeedState
  };
})(window);
