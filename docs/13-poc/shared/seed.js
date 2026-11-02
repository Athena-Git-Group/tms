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
  // attributes：訂房屬性（VIP／親子／寵物／紀念日等），接「準備工單規則」用——
  // 306/307/506 刻意各帶一種，示範一次 PMS 拉回就能命中三條不同規則。
  // roomState：房態（checkout 退房／stayover 續住／vacant 空房／out_of_service 停用維修）。
  // 602 刻意標停用維修——同樣是空房巡，基準表的「停用維修」格比「空房」格多幾分鐘；
  // 不過出廠的排房納入開關把停用維修房排除在草稿外，602 預設會出現在「因設定排除」
  // 清單裡，到租戶設定把「停用維修房」切成納入、重拉一次，就看得到它帶著基準表
  // 「停用維修」格的分鐘數進草稿——一筆資料同時示範基準表的二維差異與納入開關。
  // flags：房間當日旗標（eco 環保房＝住客掛環保牌、這次住宿少打掃／do_not_disturb
  // 請勿打擾），跟房態是兩回事——一間續住房可以同時掛環保牌。v1 旗標只影響
  // 進不進排房草稿（納入開關），不影響預估工時。
  // 505 掛請勿打擾（出廠預設排除，重拉就看得到排除清單）、601 掛環保牌（出廠預設
  // 納入，草稿卡上看得到旗標）；603 是純空房巡的空房，示範「空房」開關有東西可關。
  var PMS_TODAY = [
    { roomNo: '306', kind: '退房清', roomState: 'checkout', attributes: ['VIP'] },
    { roomNo: '307', kind: '續住清', roomState: 'stayover', attributes: ['親子'] },
    { roomNo: '308', kind: '退房清', roomState: 'checkout' },
    { roomNo: '505', kind: '夜床', roomState: 'stayover', flags: ['do_not_disturb'] },
    { roomNo: '506', kind: '退房清', roomState: 'checkout', attributes: ['寵物'] },
    { roomNo: '601', kind: '續住清', roomState: 'stayover', flags: ['eco'] },
    { roomNo: '602', kind: '空房巡', roomState: 'out_of_service' },
    { roomNo: '603', kind: '空房巡', roomState: 'vacant' }
  ];

  // PMS 整合的 mock 能力表：每家 PMS 一列，mode 是「測試連線＋能力盤點」會回報的
  // 同步能力——push＝能主動推事件（adapter 走事件訂閱）、poll＝只能輪詢（adapter
  // 每 N 分鐘去問一次）、manual＝沒有要接 PMS（今日房態改用 Excel／CSV 手動匯入）。
  // 真實環境這張表是盤點出來的結果，這裡寫死在種子裡示意。
  var PMS_PROVIDERS = [
    { key: 'in_house', name: '自家 PMS', mode: 'push', pollIntervalMin: null, note: '第一個 adapter；能主動推事件，同步走事件訂閱。' },
    { key: 'mews', name: 'Mews', mode: 'push', pollIntervalMin: null, note: '能主動推事件，同步走事件訂閱。' },
    { key: 'cloudbeds', name: 'Cloudbeds', mode: 'poll', pollIntervalMin: 5, note: '不能主動推事件，adapter 每 5 分鐘輪詢一次兜底。' },
    { key: 'manual', name: '手動匯入模式', mode: 'manual', pollIntervalMin: null, note: '不接 PMS：今日房態用 Excel／CSV 匯入，沒有回寫對象。' }
  ];

  function staffName(staffId) {
    var s = STAFF.filter(function (x) { return x.id === staffId; })[0];
    return s ? s.name : staffId;
  }

  // 房間主檔的出廠預設：收斂既有分散各處的房號（種子打掃單 301-305/502-503、
  // PMS 模擬今日房態 306-308/505-506/601-602）成單一清單，樓層一律由房號前綴推算
  // （與 engine.floorOf 同一套算法），不另外存一份容易兜不起來。
  var ROOM_NUMBERS = ['301', '302', '303', '305', '306', '307', '308', '502', '503', '505', '506', '601', '602', '603'];

  // QR token 只是示意用的短字串，重產時換一組即可；不做真正的簽章/加密。
  function genQrToken(roomNo) {
    return 'qr' + roomNo + '_' + Math.random().toString(36).slice(2, 8);
  }

  function buildDefaultRooms() {
    return ROOM_NUMBERS.map(function (roomNo) {
      return { roomNo: roomNo, active: true, qrToken: genQrToken(roomNo), qrRotatedAt: null };
    });
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
      // 清掃工作量分三層取值：房間覆寫 > 類型×房態基準格 > 類型預設。全館一套
      // 「類型→分鐘」會把行政套房與標準房、退房清與續住清的差異全抹平，排出來的
      // 班表沒人信；三層讓差異寫在該寫的層級，又不逼租戶把每格都填滿。
      cleaningStandard: { '退房清': 30, '續住清': 20, '夜床': 10, '空房巡': 5 },
      // 基準格：清掃類型 × 房態（checkout 退房／stayover 續住／vacant 空房／
      // out_of_service 停用維修），每格一個基準分鐘數。沒列的格代表該組合不適用，
      // 取值時退回上面的類型預設，租戶不必為用不到的組合編數字。
      cleaningStandardMatrix: {
        '退房清': { checkout: 30 },
        '續住清': { stayover: 20 },
        '夜床': { stayover: 10 },
        '空房巡': { vacant: 5, out_of_service: 8 }
      },
      // 房間覆寫：306 房格局特殊、誰掃都多十分鐘，退房清直接指定 40 分。出廠帶
      // 這一筆是因為 306 在 PMS 模擬房態裡，一拉回來就看得到覆寫蓋過基準格的效果。
      cleaningRoomOverrides: { '306': { '退房清': 40 } },
      // 顯示單位 minutes（分鐘）或 points（點數）。點數只是把同一個數字換個標籤
      // 當相對權重顯示，刻意不做換算——一換算點數就只是分鐘的別名，遮不住
      // 「絕對時間變成對員工的考核承諾」這個要避開的問題；底層運算一律分鐘。
      workloadDisplayUnit: 'minutes',
      shiftCapacityMin: 240,
      // 打掃刻意不掛 SLA（排房計畫管它的節奏）；種子任務建立在 3 小時前，
      // 若打掃也掛時限，一重置滿版「逾時」會蓋掉真正該被看到的臨時需求逾時。
      slaByType: { amenity: 15, repair: 60, cleaning: null, other: 30 },
      customMenuItems: [],
      // 自動化規則：觸發源（trigger）＋條件＋動作（建任務）。v1 開放兩種觸發源：
      // reservation＝訂房資料進來（拉 PMS 房態／CSV 匯入時比對訂房屬性，見 engine.js
      // generateReservationTasks）、schedule＝排程時間到（每日或每週，schedule 欄描述
      // 時點，見 engine.js scanScheduleRules）。dueAfterMin 選填：建立後 N 分鐘到期，
      // 任務帶 dueAt，逾時升級鏈改以 dueAt 起算。
      // 出廠四條（builtin:true）就是內建的第一組規則——原「準備工單規則」：
      // 訂房屬性命中就自動開準備工單。皆可改內容、停用、刪除；屬性是自由文字，
      // 租戶可自建（例如「長住」）。
      automationRules: [
        { key: 'prep_vip', trigger: 'reservation', attribute: 'VIP', title: 'VIP 迎賓準備', dept: 'housekeeping', type: 'amenity', enabled: true, builtin: true, dueAfterMin: null },
        { key: 'prep_family', trigger: 'reservation', attribute: '親子', title: '嬰兒床佈置', dept: 'housekeeping', type: 'amenity', enabled: true, builtin: true, dueAfterMin: null },
        { key: 'prep_pet', trigger: 'reservation', attribute: '寵物', title: '寵物墊準備', dept: 'housekeeping', type: 'amenity', enabled: true, builtin: true, dueAfterMin: null },
        { key: 'prep_anniversary', trigger: 'reservation', attribute: '紀念日', title: '紀念日佈置', dept: 'housekeeping', type: 'other', enabled: true, builtin: true, dueAfterMin: null }
      ],
      inspectionItems: [
        { key: 'bed', label: '床鋪' },
        { key: 'bathroom', label: '浴室' },
        { key: 'amenity', label: '備品' },
        { key: 'qr', label: 'QR 貼紙' }
      ],
      autoAssignStrategy: 'floor',
      // 排房草稿的納入開關（true＝納入）。只有這四類有開關——退房、續住是打掃的
      // 本業，開放關掉只會做出「今天的退房房全部沒人掃」的設定事故。出廠值的取捨：
      // 停用維修與請勿打擾預設排除，因為派人過去要嘛撲空要嘛打擾住客，錯派的代價
      // 比漏排高；空房與環保房預設納入，因為空房巡與環保房的續住清少做了不會立刻
      // 有人發現，漏排的代價比錯派高——寧可進草稿讓主管看著刪。環保房（eco）與
      // 請勿打擾（do_not_disturb）是房間當日旗標、不是第五種房態：一間續住房可以
      // 同時掛環保牌，塞進房態四值會把兩個獨立維度壓成一個。
      scheduleInclusion: {
        vacant: true,
        eco: true,
        out_of_service: false,
        do_not_disturb: false
      },
      ownerAlertMaxStars: 2,
      // 通知來源開關：老闆兩個來源各自獨立（住客低分評價／主管上報），
      // 櫃檯的 SLA 逾時提醒預設關——開了才會在卡片標記之外額外提醒。
      notifications: {
        ownerLowRatingEnabled: true,
        ownerManagerReportEnabled: true,
        deskSlaAlertEnabled: false
      },
      // 逾時升級鏈：任務超過 SLA 後分層通知，叫的人一層比一層大（升級是通知行為，
      // 不是新狀態——九態不動，留痕走 escalated 事件）。每層「逾時後第幾分鐘」與
      // 收件角色都開放租戶自調（差異化立場：競品多半鎖死閾值）；第一層收件人固定
      // 是接單人／部門待接池，改不了——「這單過時限了」第一個該知道的人沒有懸念。
      // afterOverdueMin 允許小數（0.5＝30 秒），讓驗收不用枯等整分鐘。
      // recipient 的 duty_manager（值班經理）在這個 POC 沒有獨立頁面，
      // 映射成「櫃檯看板＋老闆頁並列顯示」（見 engine.js ESCALATION_RECIPIENT_PAGES）。
      escalation: {
        enabled: true,
        levels: [
          { level: 1, enabled: true, afterOverdueMin: 0, recipient: 'assignee_or_pool' },
          { level: 2, enabled: true, afterOverdueMin: 2, recipient: 'manager' },
          { level: 3, enabled: true, afterOverdueMin: 5, recipient: 'duty_manager' }
        ]
      },
      // 營業參數：時區／日切只存值示意（這個 POC 沒有逐日資料，接不上真正的
      // 「今天」判斷）；退房／入住時間給排房頁顯示提示用，不改演算法；
      // 防濫用節流兩個值是 guestSubmitChecklist／guestSubmitFreetext 真的會吃的設定。
      business: {
        timezone: 'Asia/Taipei',
        dayCutoverTime: '00:00',
        checkoutTime: '11:00',
        checkinTime: '15:00',
        guestThrottleWindowMin: 10,
        guestThrottleMaxCount: 3
      },
      // 資料保留：對應 docs/11「受控去識別化」政策。去識別化期限與照片保留純存值
      // 示意；遺失物保管期限＋到期處理有消費端（desk.html 標「已逾保管期」）。
      dataRetention: {
        deidentifyDays: 365,
        lostFoundRetentionDays: 90,
        lostFoundOnExpiry: 'flag',
        lostFoundPhotoRetentionDays: 90
      },
      // 品牌：Logo／主色即時套用到住客掃碼頁；primaryColor 為 null 代表沿用預設主題色。
      brand: {
        logo: null,
        primaryColor: null
      },
      // PMS 整合設定（租戶級，一家 PMS 一組）：連線憑證（mock，只存值示意）、
      // 能力盤點結果（按「測試連線＋能力盤點」才寫入，null＝還沒盤點過）、
      // 同步範圍（今日房態／訂房屬性，關「訂房屬性」匯入時會真的丟棄屬性欄）、
      // 回寫開關——回寫永遠只有既定兩條路徑：房間狀態（查房通過 Inspected、停用
      // 維修 OOO）、minibar 計費，各自可關、不開放第三條（對應 docs/11 的紅線）。
      pms: {
        provider: 'in_house',
        credential: '',
        capability: null,
        syncScope: { occupancy: true, reservationAttributes: true },
        writeback: { roomStatus: true, minibarBilling: true }
      },
      // 部門清單：自建選單項目的部門下拉、員工的所屬部門都吃這份清單（單一真相）。
      // 停用只影響「之後還能不能選」，不回頭改動已經指派到這個部門的員工或任務。
      departments: [
        { key: 'housekeeping', name: '房務', active: true },
        { key: 'maintenance', name: '工務', active: true }
      ],
      // 班次定義：開始/結束皆為 24 小時制 HH:MM；晚班跨午夜（23:00–07:00）用
      // 「開始 > 結束」表示，判斷目前班次時要處理這種跨日情形。
      shifts: [
        { key: 'morning', name: '早班', start: '07:00', end: '15:00' },
        { key: 'afternoon', name: '中班', start: '15:00', end: '23:00' },
        { key: 'night', name: '晚班', start: '23:00', end: '07:00' }
      ]
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
      schedule: { draft: [], excluded: [], source: null, importedAt: null },
      staff: STAFF,
      rooms: buildDefaultRooms(),
      tasks: tasks,
      events: events,
      notices: [
        { id: 'ntc_seed_1', message: '今日 14:00 起 3 樓電梯保養，請改走安全梯。', actor: '小陳', ts: t0 - 2 * H, readBy: ['小美'] }
      ],
      lostFound: [
        { id: 'lf_seed_1', roomNo: '210', item: '藍色行動電源', status: 'held', foundBy: '小美', ts: t0 - 5 * H },
        // 登記時間刻意設在保管期限（預設 90 天）之外，重置就能直接看到「已逾保管期」
        // 標記，不用另外竄改資料來驗這個消費端效果。
        { id: 'lf_seed_2', roomNo: '303', item: '黑框眼鏡', status: 'held', foundBy: '阿芳', ts: t0 - 100 * 24 * H }
      ]
    };
  }

  global.TMS_SEED = {
    STAFF: STAFF,
    HOTEL: HOTEL,
    PMS_TODAY: PMS_TODAY,
    PMS_PROVIDERS: PMS_PROVIDERS,
    defaultSettings: defaultSettings,
    buildSeedState: buildSeedState,
    buildDefaultRooms: buildDefaultRooms
  };
})(window);
