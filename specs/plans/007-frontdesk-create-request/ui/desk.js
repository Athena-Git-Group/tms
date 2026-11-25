/* 櫃檯 Web 靜態雛形的示範資料與互動（沿用 004 的雛形，示範資料換成代開單的舉例，並接上代開單面板）。資料只放在這個瀏覽器分頁，讓 reviewer 關掉分頁就回到初始狀態。 */
(function () {
  'use strict';
  const Q = new URLSearchParams(location.search);
  const PAGE = document.body.dataset.page;
  const $ = (sel, root) => (root || document).querySelector(sel);
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  /* 私密瀏覽或預覽環境可能寫不進儲存區；雛形在那種環境仍要能操作，所以讀寫失敗一律當成沒有存。 */
  const ss = {
    get(k, d) { try { const v = sessionStorage.getItem(k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { sessionStorage.setItem(k, JSON.stringify(v)); } catch (e) { return; } },
    del(k) { try { sessionStorage.removeItem(k); } catch (e) { return; } }
  };
  const ls = {
    get(k, d) { try { const v = localStorage.getItem(k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { return; } }
  };

  const P = {
    board: '<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M9 5v14M15 5v14"/>',
    tray: '<path d="M3 13l3-7h12l3 7v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M3 13h5a4 4 0 0 0 8 0h5"/>',
    bell: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
    bag: '<rect x="5" y="8" width="14" height="12" rx="2.5"/><path d="M9 8a3 3 0 0 1 6 0"/>',
    sidebar: '<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M9 5v14"/>',
    chevron: '<path d="M9 5l7 7-7 7"/>',
    back: '<path d="M15 5l-7 7 7 7"/>',
    search: '<circle cx="11" cy="11" r="6"/><path d="M20 20l-4.5-4.5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    warn: '<path d="M12 4l9 16H3z"/><path d="M12 10v4M12 17v.5"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    pulse: '<path d="M3 12h4l2-6 4 12 2-6h6"/>',
    qr: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM18 14h2M14 18v2"/>',
    desk: '<rect x="3" y="5" width="18" height="14" rx="2.5"/><circle cx="12" cy="10.5" r="2.2"/><path d="M8 16.5a4 4 0 0 1 8 0"/>',
    calendar: '<rect x="4" y="6" width="16" height="14" rx="2.5"/><path d="M4 10h16M8 4v4M16 4v4"/>',
    wrench: '<path d="M14.5 6.5a4 4 0 0 0 5 5L11 20a2.1 2.1 0 0 1-3-3l8.5-8.5"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    checkCircle: '<circle cx="12" cy="12" r="9"/><path d="M8 12.5l3 3 5-6"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8v.5"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
    eyeOff: '<path d="M4 4l16 16"/><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>'
  };
  const icon = (name, cls) => '<svg class="icon ' + (cls || '') + '" viewBox="0 0 24 24" aria-hidden="true">' + P[name] + '</svg>';

  const HOTEL = '雅典娜酒店';
  const ACCOUNTS = {
    F0101: { name: '林佩君', role: 'frontdesk' }, M0001: { name: '張主管', role: 'manager' }, D0001: { name: '周值班', role: 'duty_manager' },
    H0532: { name: '阿美', role: 'housekeeping' }, F0199: { name: '吳小安', role: 'frontdesk', tempOnly: true },
    F0150: { name: '', role: 'frontdesk', locked: true }, F0160: { name: '', role: 'frontdesk', disabled: true }
  };
  const ROLE = { frontdesk: '櫃檯', manager: '主管', duty_manager: '值班主管' };
  const DESK_ROLES = ['frontdesk', 'manager', 'duty_manager'];
  const LANGS = [['zh-Hant', '繁體中文', '中文（繁體）'], ['en', 'English', '英文'], ['ja', '日本語', '日文'], ['vi', 'Tiếng Việt', '越南文']];
  /* 雛形只示範語言切換的機制，所以只收原型字典裡已經有譯文的句子（依序是日文、越南文、英文）；字典沒有的句子維持繁體中文，避免自己編譯文。 */
  const DICT = {
    '登入 TMS 櫃檯': ['TMS フロントにログイン', 'Đăng nhập TMS Lễ tân', 'Sign In to TMS Front Desk'],
    '這台裝置是櫃檯共用的，請用自己的帳號登入': ['この端末はフロント共用です。自分のアカウントでログインしてください', 'Thiết bị này dùng chung ở quầy lễ tân, hãy đăng nhập bằng tài khoản của bạn', 'This device is shared at the front desk. Sign in with your own account'],
    '員工編號': ['社員番号', 'Mã nhân viên', 'Employee ID'], '密碼': ['パスワード', 'Mật khẩu', 'Password'], '登入': ['ログイン', 'Đăng nhập', 'Sign In'],
    '忘記密碼？': ['パスワードを忘れた場合', 'Quên mật khẩu?', 'Forgot password?'], '忘記密碼': ['パスワードを忘れた場合', 'Quên mật khẩu', 'Forgot Password'],
    '員工編號或密碼不正確。': ['社員番号またはパスワードが正しくありません。', 'Mã nhân viên hoặc mật khẩu không đúng.', 'Incorrect employee ID or password.'],
    '顯示密碼': ['パスワードを表示', 'Hiện mật khẩu', 'Show password'], '隱藏密碼': ['パスワードを隠す', 'Ẩn mật khẩu', 'Hide password'],
    '好': ['OK', 'OK', 'OK'], '語言': ['言語', 'Ngôn ngữ', 'Language'], '登出': ['ログアウト', 'Đăng xuất', 'Sign Out'], '帳號': ['アカウント', 'Tài khoản', 'Account'],
    '完成': ['完了', 'Hoàn thành', 'Done'], '取消': ['キャンセル', 'Hủy', 'Cancel'], '登出？': ['ログアウトしますか？', 'Đăng xuất?', 'Sign Out?'], 'App 語言': ['App の言語', 'Ngôn ngữ ứng dụng', 'App Language'],
    '新密碼': ['新しいパスワード', 'Mật khẩu mới', 'New Password'], '確認密碼': ['パスワードの確認', 'Xác nhận mật khẩu', 'Confirm Password'], '更新密碼': ['パスワードを更新', 'Cập nhật mật khẩu', 'Update Password'],
    '至少 8 碼': ['8 文字以上', 'Tối thiểu 8 ký tự', 'At least 8 characters'], '包含英文字母': ['英字を含む', 'Có chữ cái', 'Contains a letter'], '包含數字': ['数字を含む', 'Có chữ số', 'Contains a number'], '兩次輸入相同': ['2 回の入力が一致', 'Hai lần nhập trùng khớp', 'Both entries match'],
    '中文（繁體）': ['中国語（繁体字）', 'Tiếng Trung (Phồn thể)', 'Chinese (Traditional)'], '英文': ['英語', 'Tiếng Anh', 'English'], '日文': ['日本語', 'Tiếng Nhật', 'Japanese'], '越南文': ['ベトナム語', 'Tiếng Việt', 'Vietnamese'],
    '櫃檯': ['フロント', 'Lễ tân', 'Front Desk'], '主管': ['上長', 'Quản lý', 'Supervisor'],
    '只會改變 App 介面的文字。住客、主管與同事輸入的內容（例如交辦、報修說明）維持原文。': ['変わるのは App の表示言語だけです。ゲスト・上長・同僚が入力した内容（指示や修理依頼の説明など）は原文のまま表示されます。', 'Chỉ đổi ngôn ngữ giao diện ứng dụng. Nội dung do khách, quản lý và đồng nghiệp nhập (giao việc, mô tả báo hỏng…) giữ nguyên bản gốc.', 'Only the app\'s interface text changes. Content entered by guests, supervisors and teammates (notes, issue descriptions) stays in its original language.'],
    '登出後回到登入頁，下一位同事用自己的帳號登入。還沒送出的代開單、通知草稿不會保留。': ['ログアウトするとログイン画面に戻り、次の人が自分のアカウントでログインします。未送信の代理作成や通知の下書きは保存されません。', 'Sau khi đăng xuất sẽ quay về trang đăng nhập để đồng nghiệp tiếp theo đăng nhập bằng tài khoản của mình. Phiếu tạo hộ và bản nháp thông báo chưa gửi sẽ không được lưu.', 'After signing out, the next colleague signs in with their own account. Unsent requests and notice drafts won\'t be kept.'],
    '請主管在後台重設密碼。重設後你會拿到臨時密碼，第一次登入時要改成自己的密碼。': ['管理画面で主任にパスワードをリセットしてもらってください。仮パスワードが発行され、初回ログイン時に自分のパスワードへ変更します。', 'Hãy nhờ quản lý đặt lại mật khẩu trong hệ thống quản trị. Bạn sẽ nhận mật khẩu tạm và phải đổi sang mật khẩu của mình ở lần đăng nhập đầu tiên.', 'Ask your supervisor to reset your password in the admin console. You\'ll get a temporary password and change it the first time you sign in.']
  };
  /* 介面語言記在這台裝置上、每人一筆，另記登入頁現在的語言；登入憑證放在分頁裡，兩者分開存，關掉分頁才不會連語言一起忘掉。 */
  const Lang = {
    browser() { const b = (navigator.language || 'zh').toLowerCase().split('-')[0]; return { en: 'en', ja: 'ja', vi: 'vi' }[b] || 'zh-Hant'; },
    store() { return ls.get('deskLang', { page: null, users: {} }); },
    page() { return this.store().page || this.browser(); },
    setPage(l) { const s = this.store(); s.page = l; ls.set('deskLang', s); },
    forUser(no) { const s = this.store(); if (!s.users[no]) { s.users[no] = this.page(); ls.set('deskLang', s); } return s.users[no]; },
    setUser(no, l) { const s = this.store(); s.users[no] = l; s.page = l; ls.set('deskLang', s); },
    name(l) { return (LANGS.find((x) => x[0] === l) || LANGS[0])[1]; }
  };
  let LANG = 'zh-Hant';
  const t = (zh) => { const i = ['ja', 'vi', 'en'].indexOf(LANG); return i >= 0 && DICT[zh] ? DICT[zh][i] : zh; };
  const session = () => ss.get('deskSession', null);
  const me = () => { const s = session(); return s ? Object.assign({ no: s.no }, ACCOUNTS[s.no]) : null; };
  const isManager = () => { const m = me(); return !!m && (m.role === 'manager' || m.role === 'duty_manager'); };

  function showAlert(title, msg, actions) {
    closeAlert();
    const host = document.createElement('div');
    host.className = 'scrim alert'; host.id = 'alert';
    host.innerHTML = '<div class="dialog" role="alertdialog" aria-label="' + esc(title) + '"><div class="dialog-text"><b>' + esc(title) + '</b><span>' + esc(msg) + '</span></div><div class="dialog-btns">' +
      actions.map((a, i) => '<button data-i="' + i + '" class="' + (a.cls || '') + '">' + esc(a.label) + '</button>').join('') + '</div></div>';
    (document.querySelector('.app') || document.body).appendChild(host);
    if (!document.querySelector('.app')) host.style.position = 'fixed';
    const prev = document.activeElement;
    host.addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; e.stopPropagation(); closeAlert(); if (prev && prev.focus) prev.focus(); const a = actions[+b.dataset.i]; if (a.fn) a.fn(); });
    host.querySelector('button:last-child').focus();
  }
  function closeAlert() { const a = $('#alert'); if (a) a.remove(); }
  const ok = (fn) => [{ label: t('好'), cls: 'strong', fn }];

  function applyI18n() {
    document.documentElement.lang = LANG;
    document.querySelectorAll('[data-t]').forEach((el) => { el.textContent = t(el.dataset.t); });
    document.querySelectorAll('[data-t-label]').forEach((el) => { el.setAttribute('aria-label', t(el.dataset.tLabel)); });
  }
  function langMenu() {
    const btn = $('#langBtn'), menu = $('#langMenu');
    const draw = () => {
      $('#langName').textContent = Lang.name(LANG);
      menu.innerHTML = LANGS.map((l) => '<button role="menuitemradio" aria-checked="' + (l[0] === LANG) + '" data-l="' + l[0] + '"><span class="tick">' + (l[0] === LANG ? icon('check', 'sm') : '') + '</span><span>' + l[1] + '</span></button>').join('');
    };
    btn.addEventListener('click', (e) => { e.stopPropagation(); menu.hidden = !menu.hidden; btn.setAttribute('aria-expanded', String(!menu.hidden)); });
    menu.addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; LANG = b.dataset.l; const s = session(); if (s) Lang.setUser(s.no, LANG); else Lang.setPage(LANG); menu.hidden = true; draw(); applyI18n(); });
    document.addEventListener('click', () => { menu.hidden = true; });
    draw();
  }
  function eyeToggle(btn, input) {
    const draw = () => { const shown = input.type === 'text'; btn.innerHTML = icon(shown ? 'eye' : 'eyeOff'); btn.dataset.tLabel = shown ? '隱藏密碼' : '顯示密碼'; btn.setAttribute('aria-label', t(btn.dataset.tLabel)); };
    btn.addEventListener('click', () => { input.type = input.type === 'text' ? 'password' : 'text'; draw(); });
    draw();
  }
  function initLogin() {
    /* 打開登入頁就清掉這個分頁的登入狀態，登入頁才不會留著上一位的身分。 */
    ss.del('deskSession'); ss.del('deskUi'); ss.del('deskUndo'); ss.del('deskData');
    LANG = Lang.page();
    if (Q.get('hotel') === 'none') { const h = $('.auth-hotel'); if (h) h.hidden = true; }
    const id = $('#empNo'), pw = $('#pw'), btn = $('#submit'), err = $('#err'), card = $('#notice');
    langMenu(); eyeToggle($('#eye'), pw);
    const clear = () => { err.hidden = true; card.hidden = true; };
    const notice = (title, body) => { card.hidden = false; $('#noticeTitle').textContent = title; $('#noticeBody').textContent = body; };
    const sync = () => { btn.disabled = !(id.value && pw.value); };
    id.addEventListener('input', () => { clear(); sync(); });
    pw.addEventListener('input', () => { clear(); sync(); });
    $('#forgot').addEventListener('click', () => showAlert(t('忘記密碼'), t('請主管在後台重設密碼。重設後你會拿到臨時密碼，第一次登入時要改成自己的密碼。'), ok()));
    $('#form').addEventListener('submit', (e) => {
      e.preventDefault(); if (btn.disabled) return; clear();
      const no = id.value, a = ACCOUNTS[no], p = pw.value;
      const wrong = () => { err.hidden = false; err.textContent = t('員工編號或密碼不正確。'); pw.value = ''; sync(); };
      if (Q.get('fail') === 'net') { err.hidden = false; err.textContent = '現在連不上系統，請檢查網路連線後再試一次。'; return; }
      if (!a) return wrong();
      if (a.locked) { pw.value = ''; sync(); return notice('暫時無法登入', '連續輸錯 5 次，請 15 分鐘後再試，或請主管在後台重設密碼。'); }
      if (a.disabled) { pw.value = ''; sync(); return notice('這個帳號已停用', '請主管在後台重新啟用後再登入。'); }
      const temp = p === 'temp-0000';
      if (!temp && (p !== 'tms1234' || a.tempOnly)) return wrong();
      if (!temp && DESK_ROLES.indexOf(a.role) < 0) return wrong();
      ss.set('deskSession', { no, mustChange: temp }); Lang.forUser(no);
      location.href = temp ? 'change-password.html' : 'board.html';
    });
    if (ss.get('deskBlocked', false)) { ss.del('deskBlocked'); notice('這個帳號不能使用櫃檯畫面', '新密碼已經生效。櫃檯畫面只開放櫃檯、主管與值班主管的帳號。'); }
    applyI18n(); sync();
  }
  function initPassword() {
    const s = session();
    if (!s || !s.mustChange) { location.replace(s ? 'board.html' : 'index.html'); return; }
    LANG = Lang.forUser(s.no);
    const nw = $('#nw'), cf = $('#cf'), btn = $('#submit'), err = $('#err');
    langMenu(); eyeToggle($('#eye1'), nw); eyeToggle($('#eye2'), cf);
    const rules = () => [nw.value.length >= 8, /[A-Za-z]/.test(nw.value), /\d/.test(nw.value), !!nw.value && nw.value === cf.value];
    const sync = () => { const r = rules(); document.querySelectorAll('#rules li').forEach((li, i) => li.classList.toggle('ok', r[i])); btn.disabled = !r.every(Boolean); err.hidden = true; };
    nw.addEventListener('input', sync); cf.addEventListener('input', sync);
    $('#logout').addEventListener('click', () => { Lang.setPage(LANG); location.href = 'index.html'; });
    $('#form').addEventListener('submit', (e) => {
      e.preventDefault(); if (btn.disabled) return;
      if (Q.get('fail') === 'net') { err.hidden = false; err.textContent = '沒有更新成功，請檢查網路連線後再試一次。'; return; }
      if (nw.value === 'temp-0000') { err.hidden = false; err.textContent = '新密碼不能和臨時密碼相同。'; return; }
      if (DESK_ROLES.indexOf(ACCOUNTS[s.no].role) < 0) { ss.set('deskBlocked', true); Lang.setPage(LANG); location.href = 'index.html'; return; }
      ss.set('deskSession', { no: s.no, mustChange: false }); location.href = 'board.html';
    });
    applyI18n(); sync();
  }

  /* 示範資料的「現在」固定從 10/26 週一 14:10 起跳，再加上分頁開啟後經過的時間；這樣文件裡寫的分鐘數對得上，5 秒復原與等待時間也會真的走。 */
  if (Q.has('reset')) { ss.del('deskT0'); ss.del('deskData'); ss.del('deskUi'); ss.del('deskUndo'); ss.del('deskFresh'); }
  const AS = Q.get('as');
  if (PAGE !== 'login' && PAGE !== 'password' && AS && ACCOUNTS[AS] && DESK_ROLES.indexOf(ACCOUNTS[AS].role) >= 0 && (session() || {}).no !== AS) { ss.set('deskSession', { no: AS, mustChange: false }); ss.del('deskData'); ss.del('deskUi'); ss.del('deskUndo'); }
  const BASE = new Date(2026, 9, 26, 14, 10, 0).getTime();
  const T0 = (() => { let v = ss.get('deskT0', null); if (!v) { v = Date.now(); ss.set('deskT0', v); } return v; })();
  const now = () => BASE + (Date.now() - T0);
  const ago = (min) => BASE - min * 60000;
  const DEPT = {
    housekeeping: { label: '房務', dot: '#0C7680', fg: '#0C7680', avBg: '#C8E8ED', avFg: '#0C7680' },
    engineering: { label: '工務', dot: '#FF8E2B', fg: '#944B00', avBg: '#FFDCC5', avFg: '#703700' },
    frontdesk: { label: '未分部門', dot: '#AEAEB2', fg: '#3C3C43', avBg: '#E5E5EA', avFg: '#3C3C43' }
  };
  const KIND = { amenity: '備品', repair: '修繕', cleaning: '打掃', other: '其他' };
  const SRC = { guest: ['住客掃碼', 'qr'], desk: ['櫃檯開單', 'desk'], system: ['系統排房', 'calendar'], staff: ['同仁報修', 'wrench'] };
  const COLS = [
    ['triage', '待派單', '#FF9500', '沒有要派的單'], ['pool', '待認領', '#0C7680', '沒有等認領的單'], ['accepted', '已接', '#8E8E93', '—'], ['doing', '進行中', '#34C759', '—'],
    ['done', '已完成', '#1A6B34', '—'], ['interrupted', '中斷', '#FF3B30', '沒有中斷的單'], ['deferred', '延後中', '#8E8E93', '沒有延後的單'], ['closed', '已結案', '#AEAEB2', '—']
  ];
  const GUESTS = {
    305: { name: '王大明 先生', meta: '10/26–10/28', vip: true }, 507: { name: 'Sato Kenji', meta: '10/24–10/28', vip: true }, 412: { name: 'Sato Yuki 様', meta: '10/25–10/27' },
    508: { name: 'Emily Clark', meta: '10/25–10/28' }, 311: { name: '林志偉', meta: '10/26–10/27' }, 214: { name: '陳美玲', meta: '10/24–10/27', vip: true },
    506: { name: '黃建國', meta: '10/26–10/28' }, 402: { name: '李雅婷', meta: '10/25–10/27' }, 318: { name: '吳宗翰', meta: '10/26–10/27' },
    409: { name: '許文傑', meta: '10/25–10/26' }, 510: { name: '鄭家豪', meta: '10/26–10/30' }, 408: { name: '蔡宜君', meta: '10/25–10/27' },
    405: { name: '劉怡如', meta: '10/23–10/27' }, 306: { name: '李小姐', meta: '10/26–10/27' }, 410: { name: '楊淑芬', meta: '10/25–10/27' },
    215: { name: '周冠宇', meta: '10/26–10/28' }, 216: { name: '謝佩珊', meta: '10/24–10/27' }, 209: { name: '高志明', meta: '10/24–10/28' },
    502: { name: '羅曉雯', meta: '10/26–10/27' }, 312: { name: '簡宏達', meta: '10/25–10/27' }, 316: { name: '江欣怡', meta: '10/26–10/28' },
    207: { name: '黃小姐', meta: '10/26–10/27' }, 220: { name: '吳先生', meta: '10/25–10/28' }, 204: { name: '林先生', meta: '10/26–10/29' },
    303: { name: '高先生', meta: '10/25–10/27' }, 310: { name: '陳小姐', meta: '10/26–10/28' }
  };
  /* 房間清單：2 至 5 樓、每層 01 至 20 號。沒有出現在 GUESTS 的房是空房（例如 308）。 */
  const ROOMS = [2, 3, 4, 5].reduce((a, f) => a.concat(Array.from({ length: 20 }, (_, i) => f + String(i + 1).padStart(2, '0'))), []);
  const hk = 'housekeeping', eng = 'engineering', fd = 'frontdesk';
  /* 時間欄位用「幾分鐘前」寫，載入時才換成時刻，示範資料才不會綁死在某一天：c 建立、w 待認領的等待起算、a 認領、s 開始、d 完成、e 最後一步。 */
  const SEED = [
    { id: 'T-1057', room: '508', kind: 'other', dept: fd, src: 'guest', state: 'triage', free: 1, title: 'Could I get one more blanket?', c: 0.4 },
    { id: 'T-1053', room: '305', kind: 'other', dept: fd, src: 'guest', state: 'triage', free: 1, title: '房間有怪聲', c: 3 },
    { id: 'T-1054', room: '305', kind: 'other', dept: fd, src: 'guest', state: 'triage', free: 1, title: '早餐幾點', c: 3 },
    { id: 'T-1055', room: '507', kind: 'other', dept: fd, src: 'guest', state: 'triage', free: 1, title: '想多一顆枕頭，另外檯燈不亮', c: 12 },
    { id: 'T-1050', room: '412', kind: 'other', dept: fd, src: 'guest', state: 'triage', free: 1, title: '電視遙控器沒有反應', orig: 'テレビのリモコンが反応しません', origLang: '日文', c: 23 },
    { id: 'T-1043', room: '402', kind: 'amenity', dept: hk, src: 'desk', by: '陳怡安', state: 'pool', item: '礦泉水', title: '礦泉水 ×2', c: 4 },
    /* 類似單提醒用：305 住客勾選的毛巾還在待認領、礦泉水小美正在做；220 住客打字「冷氣不冷」已對應到冷氣；412 也有一張毛巾（別間房，不算）。 */
    { id: 'T-1045', room: '305', kind: 'amenity', dept: hk, src: 'guest', state: 'pool', item: '毛巾', title: '毛巾 ×2', c: 12 },
    { id: 'T-1042', room: '412', kind: 'amenity', dept: hk, src: 'guest', state: 'pool', item: '毛巾', title: '毛巾 ×1', c: 6 },
    { id: 'W-0150', room: '220', kind: 'repair', dept: eng, src: 'guest', state: 'pool', free: 1, item: '冷氣', hit: '關鍵字「冷氣」命中工務規則', title: '冷氣不冷', c: 23 },
    { id: 'T-1046', room: '305', kind: 'amenity', dept: hk, src: 'guest', state: 'doing', item: '礦泉水', title: '礦泉水 ×1', who: '小美', how: 'claim', c: 12, a: 10, s: 7 },
    /* 305 已完成、中斷、延後中的同品項單：不觸發類似單提醒。 */
    { id: 'T-1014', room: '305', kind: 'amenity', dept: hk, src: 'guest', state: 'done', item: '毛巾', title: '毛巾 ×2', who: '小美', how: 'claim', c: 90, a: 85, s: 80, d: 70 },
    { id: 'T-1016', room: '305', kind: 'amenity', dept: hk, src: 'guest', state: 'interrupted', item: '浴巾', title: '浴巾 ×1', who: '阿珠', how: 'claim', note: '浴巾送到門口，住客不在。', c: 60, a: 55, s: 50, e: 40 },
    { id: 'T-1017', room: '305', kind: 'amenity', dept: hk, src: 'guest', state: 'deferred', item: '拖鞋', title: '拖鞋 ×1', how: 'claim', deferBy: '張主管', c: 50, e: 30 },
    { id: 'T-1040', room: '311', kind: 'amenity', dept: hk, src: 'guest', state: 'pool', item: '毛巾', title: '毛巾 ×2', c: 9 },
    { id: 'T-1041', room: '214', kind: 'amenity', dept: hk, src: 'guest', state: 'pool', title: '加一條浴巾', c: 12 },
    { id: 'W-0142', room: '506', kind: 'repair', dept: eng, src: 'staff', by: '阿美', state: 'pool', title: '冷氣不冷', c: 23 },
    { id: 'T-1030', room: '203', kind: 'cleaning', dept: hk, src: 'system', state: 'accepted', title: '退房打掃', who: '小陳', how: 'plan', sop: [0, 8], c: 370, a: 370 },
    { id: 'T-1031', room: '318', kind: 'amenity', dept: hk, src: 'guest', state: 'accepted', title: '吹風機', who: '阿美', how: 'claim', c: 11, a: 6 },
    { id: 'W-0132', room: '409', kind: 'repair', dept: eng, src: 'guest', state: 'accepted', title: '馬桶堵塞', who: '志豪', how: 'reassign', howBy: '張主管', howFrom: '阿德', c: 205, a: 185 },
    { id: 'T-1020', room: '305', kind: 'amenity', dept: hk, src: 'guest', state: 'doing', item: '牙刷組', title: '牙刷組 ×2', who: '阿美', how: 'claim', c: 20, a: 14, s: 8 },
    { id: 'T-1021', room: '204', kind: 'cleaning', dept: hk, src: 'system', state: 'doing', title: '退房打掃', who: '小陳', how: 'plan', sop: [3, 8], c: 370, a: 370, s: 25 },
    { id: 'W-0122', room: '510', kind: 'repair', dept: eng, src: 'guest', state: 'doing', title: '浴室燈不亮', who: '阿德', how: 'claim', c: 40, a: 31, s: 15 },
    { id: 'T-1013', room: '408', kind: 'amenity', dept: hk, src: 'guest', state: 'done', item: '毛巾', title: '毛巾 ×2', who: '阿珠', how: 'claim', c: 35, a: 28, s: 20, d: 8 },
    { id: 'T-1011', room: '302', kind: 'cleaning', dept: hk, src: 'system', state: 'done', title: '退房打掃', who: '阿美', how: 'plan', c: 370, a: 370, s: 60, d: 15 },
    { id: 'T-1010', room: '207', kind: 'cleaning', dept: hk, src: 'system', state: 'done', title: '退房打掃', who: '阿珠', how: 'plan', c: 370, a: 370, s: 75, d: 30 },
    { id: 'W-0112', room: '215', kind: 'repair', dept: eng, src: 'staff', by: '小陳', state: 'done', title: '水龍頭滴水', who: '志豪', how: 'claim', c: 150, a: 120, s: 90, d: 50 },
    { id: 'T-1006', room: '216', kind: 'cleaning', dept: hk, src: 'system', state: 'interrupted', title: '續住打掃', who: '阿珠', how: 'plan', bySelf: 1, reason: '住客還在房內', c: 370, a: 370, s: 30, e: 12 },
    { id: 'T-1005', room: '303', kind: 'cleaning', dept: hk, src: 'system', state: 'interrupted', title: '退房打掃', who: '阿芬', how: 'plan', note: '浴室洗好了，床還沒鋪。', c: 370, a: 370, s: 80, e: 35 },
    { id: 'T-1004', room: '405', kind: 'cleaning', dept: hk, src: 'system', state: 'deferred', title: '續住打掃', who: '阿美', how: 'plan', bySelf: 1, reason: '住客掛請勿打擾', c: 370, a: 370, e: 45 },
    { id: 'T-1003', room: '501', kind: 'cleaning', dept: hk, src: 'system', state: 'deferred', title: '空房巡', how: 'plan', deferBy: '張主管', c: 370, a: 370, e: 130 },
    { id: 'T-0998', room: '306', kind: 'other', dept: fd, src: 'guest', state: 'done', free: 1, title: '退房時間可以延到幾點？', answered: 1, reply: '退房時間是中午 12:00，需要延遲退房請撥 0 找櫃檯。', answeredBy: '林佩君', c: 39, e: 35 },
    { id: 'T-1001', room: '201', kind: 'cleaning', dept: hk, src: 'system', state: 'inspected', inspection: 'passed', title: '退房打掃', who: '小陳', how: 'plan', inspector: '張主管', c: 370, a: 370, s: 150, d: 100, e: 60 },
    { id: 'T-1002', room: '209', kind: 'cleaning', dept: hk, src: 'system', state: 'inspected', inspection: 'waived', title: '續住打掃', who: '阿芬', how: 'plan', c: 370, a: 370, s: 120, d: 80, e: 80 },
    { id: 'W-0096', room: '312', kind: 'repair', dept: eng, src: 'staff', by: '阿美', state: 'closed', title: '檯燈不亮', who: '阿德', how: 'claim', verifiedBy: '張主管', c: 300, a: 280, s: 250, d: 200, e: 150 },
    { id: 'T-0997', room: '410', kind: 'amenity', dept: hk, src: 'guest', state: 'cancelled', cancel: 'guest_withdrew', title: '加床', c: 118, e: 110 },
    { id: 'W-0095', room: '219', kind: 'repair', dept: eng, src: 'staff', by: '阿珠', state: 'closed', title: '浴室排水慢', who: '志豪', how: 'claim', noAction: 1, c: 260, a: 240, s: 222, d: 215, e: 215 },
    { id: 'T-0994', room: '502', kind: 'amenity', dept: hk, src: 'guest', state: 'cancelled', cancel: 'manager_void', cancelBy: '張主管', title: '礦泉水 ×2', c: 250, e: 235 },
    { id: 'T-0993', room: '220', kind: 'cleaning', dept: hk, src: 'system', state: 'cancelled', cancel: 'plan_superseded', title: '空房巡', how: 'plan', c: 370, a: 370, e: 290 },
    { id: 'W-0092', room: '316', kind: 'repair', dept: eng, src: 'staff', by: '阿美', state: 'cancelled', cancel: 'staff_withdrew', title: '門鎖沒電', c: 320, e: 305 }
  ];
  const LOADMODE = Q.get('load');
  const Store = {
    data: null,
    load() {
      this.data = ss.get('deskData', null);
      if (!this.data) {
        const tasks = SEED.map((x) => { const o = Object.assign({ floor: +x.room[0], log: [] }, x); ['c', 'w', 'a', 's', 'd', 'e'].forEach((k) => { if (o[k] != null) o[k] = ago(o[k]); }); return o; });
        this.data = { tasks, sent: {}, answered: {} };
      }
      if (LOADMODE === 'empty') this.data.tasks = [];
      if (LOADMODE === 'done') { this.data.tasks = this.data.tasks.filter((x) => x.state !== 'triage'); const no = (session() || {}).no; this.data.sent[no] = 5; this.data.answered[no] = 2; }
    },
    /* 用網址切到空資料這類示範狀態時不存回分頁，否則拿掉參數後看板還是空的。 */
    save() { if (!LOADMODE) ss.set('deskData', this.data); },
    get tasks() { return this.data.tasks; },
    byId(id) { return this.data.tasks.find((x) => x.id === id); }
  };

  /* 分鐘數算出負數時當成 0，裝置時鐘比後端快也不會顯示負的等待時間或提早變色。 */
  const mins = (ms) => Math.max(0, Math.floor((now() - ms) / 60000));
  const hm = (ms) => { const d = new Date(ms); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
  const agoText = (ms) => { const x = mins(ms); return x < 1 ? '剛剛' : x + ' 分鐘前'; };
  const colOf = (x) => (x.answered ? 'closed' : ['inspected', 'closed', 'cancelled'].indexOf(x.state) >= 0 ? 'closed' : x.state);
  const waitMs = (x) => (x.state === 'pool' ? (x.w || x.c) : x.c);
  const tone = (m) => (m >= 20 ? 'over' : m >= 10 ? 'warn' : '');
  function pill(x) {
    const col = colOf(x);
    if (col === 'triage' || col === 'pool') { const m = mins(waitMs(x)); return [m < 1 ? '剛剛' : col === 'pool' && m >= 10 ? '逾時 · ' + (m - 10) + ' 分' : '等 ' + m + ' 分', tone(m)]; }
    if (col === 'accepted') return [x.how === 'plan' ? '今日排房' : '接 ' + mins(x.a) + ' 分', ''];
    if (col === 'doing') return ['做 ' + mins(x.s) + ' 分', ''];
    if (col === 'interrupted') return ['停 ' + mins(x.e) + ' 分', 'stop'];
    if (col === 'deferred') return ['待重排', ''];
    if (col === 'done') return [hm(x.d), 'ok'];
    return [hm(x.e), ''];
  }
  const shown = (x) => (x.free ? '「' + x.title + '」' : x.title);
  function subOf(x) {
    if (x.state === 'triage') return '手動派單' + (x.orig ? ' · 已翻譯' : '');
    if (x.state === 'interrupted') return x.bySelf ? x.reason : (x.note || '');
    if (x.state === 'deferred') return x.bySelf ? x.who + ' 延後（' + x.reason + '）' : x.deferBy + ' 延後';
    if (x.answered) return '已當場回答';
    if (x.state === 'done' && x.kind === 'cleaning') return '等主管查房';
    return '';
  }
  function tagOf(x) {
    if (x.answered) return ['已回答', 'green'];
    switch (x.state) {
      case 'triage': return ['待派單', 'yellow'];
      case 'pool': return ['待認領', 'teal'];
      case 'accepted': return ['已接', 'grey'];
      case 'doing': return ['進行中', 'solid'];
      case 'done': return [x.kind === 'cleaning' ? '待查房' : x.kind === 'repair' ? '已完修' : '已完成', 'green'];
      case 'inspected': return [x.inspection === 'waived' ? '免查房' : '查房通過', 'green'];
      case 'closed': return ['已結案', 'green'];
      case 'interrupted': return ['中斷', 'red'];
      case 'deferred': return ['延後中', 'grey'];
      default: return ['已取消', 'grey'];
    }
  }
  function infoOf(x) {
    const d = DEPT[x.dept].label;
    if (x.answered) return ['櫃檯已當場回答', '「' + x.reply + '」— ' + x.answeredBy, 'green'];
    switch (x.state) {
      case 'pool': return [d + '待認領中，等同仁接單', '已等 ' + mins(waitMs(x)) + ' 分。同仁認領後這裡會即時更新。', 'teal'];
      case 'accepted': {
        const how = x.how === 'plan' ? '班前排房 指派' : x.how === 'assign' ? x.howBy + ' ' + hm(x.a) + ' 從待認領指派 · 從現在開始計時' : x.how === 'reassign' ? x.howBy + ' ' + hm(x.a) + ' 轉派（原：' + x.howFrom + '） · 從現在重新計時' : '認領於 ' + hm(x.a);
        return [x.who + ' 已接，還沒開始', how + '。', ''];
      }
      case 'doing': return [x.who + ' 處理中 · 已 ' + mins(x.s) + ' 分', '開始於 ' + hm(x.s) + '。住客來問時可以說「同仁正在處理」。', 'green'];
      case 'done':
        if (x.kind === 'cleaning') return ['已完成，等主管查房', '查房通過後房況才會回寫 PMS 變成可售。', 'green'];
        if (x.kind === 'repair') return ['已完修 · 等主管驗收', '', 'green'];
        return ['已完成', '完成於 ' + hm(x.d) + '，住客會收到通知並可以評分。', 'green'];
      case 'inspected': return x.inspection === 'waived' ? ['已完成 · 免查房', '這類打掃不用查房，完成時直接結案。', 'green'] : ['查房通過，可售', '房況已回寫 PMS。', 'green'];
      case 'closed': return [x.noAction ? '已結案 · 不需處理' : '已結案 · ' + x.verifiedBy + '驗收', '', 'green'];
      case 'interrupted': return x.bySelf ? [x.who + ' 暫停處理，任務中斷', '原因：' + x.reason, 'red'] : [x.who + ' 請假，任務中斷', '做到哪：' + (x.note || '—'), 'red'];
      case 'deferred': return x.bySelf ? [x.who + ' 延後處理', '原因：' + x.reason, ''] : ['主管延後', x.deferBy + ' 延後', ''];
      case 'cancelled':
        return ['已取消', { guest_withdrew: '住客在還沒有人認領前自己收回，不計入任何統計。', manager_void: x.cancelBy + ' ' + hm(x.e) + ' 取消。不計入同仁的完成數。', plan_superseded: '排房調整後不再需要這張單。不計入同仁的完成數。', staff_withdrew: x.by + ' ' + hm(x.e) + ' 撤回報修。不計入同仁的完成數。' }[x.cancel], ''];
      default: return null;
    }
  }
  function eventsOf(x) {
    const ev = []; const g = GUESTS[x.room]; const d = DEPT[x.dept].label;
    const guest = '住客' + (g ? '（' + g.name + '）' : '');
    const C = { brand: '#0C7680', sys: '#8E8E93', warn: '#FF9500', desk: '#5E5CE6', done: '#34C759' };
    const add = (at, by, text, c) => ev.push({ at, by, text, c });
    if (x.src === 'system') add(x.c, '系統排房', '班前排房，指派給 ' + (x.who || '（未指派）'), C.brand);
    else if (x.src === 'desk') add(x.c, x.by, '櫃檯代開「' + x.title + '」，進' + d + '待認領', C.desk);
    else if (x.src === 'staff') add(x.c, x.by, '報修「' + x.title + '」，進' + d + '待認領', C.brand);
    else if (x.free) { add(x.c, guest, '掃碼送出文字需求「' + (x.orig || x.title) + '」', C.brand); if (x.hit) add(x.c + 1000, '系統', x.hit + '，進' + d + '待認領', C.sys); else add(x.c + 1000, '系統', '規則沒有命中，等櫃檯派單', C.warn); }
    else add(x.c, guest, '掃碼勾選「' + x.title + '」，依選單進' + d + '待認領', C.brand);
    if (x.how === 'claim' && x.who) add(x.a, x.who, x.who + ' 認領', C.brand);
    if (x.how === 'reassign') add(x.a, x.howBy, '轉派給 ' + x.who + '（原：' + x.howFrom + '）', C.warn);
    if (x.s) add(x.s, x.who, '開始處理', C.brand);
    if (x.d) add(x.d, x.who, '回報完成' + (x.kind === 'cleaning' && x.inspection !== 'waived' ? '，等主管查房' : ''), C.done);
    if (x.state === 'inspected' && x.inspection === 'passed') add(x.e, x.inspector, '查房全部通過，房況回寫 PMS', C.done);
    if (x.state === 'closed' && x.verifiedBy) add(x.e, x.verifiedBy, '驗收通過，結案', C.done);
    if (x.state === 'interrupted') add(x.e, x.bySelf ? x.who : '系統', x.bySelf ? '暫停處理（' + x.reason + '）' : x.who + ' 請假，進行中的任務標記中斷', C.warn);
    if (x.state === 'deferred') add(x.e, x.bySelf ? x.who : x.deferBy, x.bySelf ? '延後（' + x.reason + '）' : '延後', C.warn);
    if (x.state === 'cancelled') add(x.e, x.cancel === 'guest_withdrew' ? guest : x.cancel === 'manager_void' ? x.cancelBy : x.cancel === 'staff_withdrew' ? x.by : '系統', { guest_withdrew: '住客自己收回需求（還沒有人認領）', manager_void: '取消這張單', plan_superseded: '排房調整，這張單取消', staff_withdrew: '撤回報修' }[x.cancel], C.warn);
    (x.log || []).forEach((l) => add(l.at, l.by, l.text, C[l.c] || C.sys));
    if (x.answered) add(x.e, x.answeredBy, '櫃檯當場回答並結案：「' + x.reply + '」', C.desk);
    return ev.sort((p, q) => p.at - q.at);
  }

  /* 篩選、房號搜尋、泳道與側欄的選擇跟登入狀態一起放在分頁裡，重新整理後才還在；面板與浮條只放在記憶體，重新整理就關掉。 */
  const ui = Object.assign({ filter: null, q: '', lanes: 'none', sb: null }, ss.get('deskUi', {}));
  const saveUi = () => ss.set('deskUi', { filter: ui.filter, q: ui.q, lanes: ui.lanes, sb: ui.sb });
  const view = { detail: null, sheet: null, account: null, undo: null, conn: false, load: 'ok', flash: {}, fresh: {} };
  /* 還沒自己按過側欄時，總覽看板收起、待派單頁打開；詳情面板開著時一律收起，而且不算使用者自己的選擇。 */
  const sbOpen = () => !view.detail && (ui.sb == null ? PAGE === 'triage' : ui.sb);
  const triage = () => Store.tasks.filter((x) => x.state === 'triage').sort((p, q) => q.c - p.c);
  const pool = () => Store.tasks.filter((x) => x.state === 'pool');

  function renderShell() {
    const m = me();
    $('.app').innerHTML =
      '<nav class="sidebar" aria-label="櫃檯功能">' +
        '<div class="sb-head"><div class="sb-mark">' + icon('desk') + '</div><div class="grow"><div class="sb-title">TMS 櫃檯</div><div class="sb-sub">' + HOTEL + '</div></div>' +
        '<button class="icon-btn" id="sbClose" aria-label="收合側欄">' + icon('sidebar') + '</button></div>' +
        '<div class="sb-mid"><div class="nav">' +
          '<a class="nav-item" href="board.html" data-nav="board"' + (PAGE === 'board' ? ' aria-current="page"' : '') + '>' + icon('board') + '<span class="grow">總覽看板</span></a>' +
          '<a class="nav-item" href="triage.html" data-nav="triage"' + (PAGE === 'triage' ? ' aria-current="page"' : '') + '>' + icon('tray') + '<span class="grow">待派單</span><span class="badge" id="badge" hidden></span></a>' +
          '<button class="nav-item" data-inert>' + icon('bell') + '<span class="grow">通知板</span></button>' +
          '<button class="nav-item" data-inert>' + icon('bag') + '<span class="grow">遺失物</span></button>' +
        '</div>' + (isManager() ? '<button class="nav-set" data-inert><span class="grow">設定</span>' + icon('chevron') + '</button>' : '') + '</div>' +
        '<button class="me-card" id="meCard" aria-haspopup="dialog"><span class="avatar" aria-hidden="true">' + m.name.slice(-1) + '</span><span class="grow"><span class="me-name">' + m.name + '</span><span class="me-role">' + ROLE[m.role] + ' · ' + m.no + '</span></span>' + icon('chevron') + '</button>' +
      '</nav>' +
      '<div class="main">' +
        '<header class="toolbar"><button class="icon-btn" id="sbOpen" aria-label="打開側欄">' + icon('sidebar') + '</button>' +
          '<div class="grow"><h1 class="page-title">' + (PAGE === 'board' ? '總覽看板' : '待派單') + '</h1><span class="page-sub" id="pageSub"></span></div>' +
          (PAGE === 'board' ? '<label class="search"><span class="sr">搜尋房號</span>' + icon('search') + '<input id="search" type="search" inputmode="numeric" maxlength="4" placeholder="搜尋房號" autocomplete="off"></label>' : '') +
          '<button class="btn-create" id="btnCreate" aria-haspopup="dialog">' + icon('plus') + '代開單</button></header>' +
        '<div class="conn-bar" id="connBar" role="status" hidden>' + icon('warn') + '<b>即時連線異常</b><span>看板資料可能延遲，每 5 秒自動更新</span></div>' +
        '<div id="page" class="' + (PAGE === 'board' ? 'board-page' : 'triage-page') + '"></div>' +
      '</div><div id="detailHost"></div><div id="undoHost"></div><div id="sheetHost"></div>';
    $('#sbClose').onclick = () => { ui.sb = false; saveUi(); render(); };
    $('#sbOpen').onclick = () => { view.detail = null; ui.sb = true; saveUi(); render(); };
    $('#meCard').onclick = () => { view.account = 'root'; renderSheet(); };
    /* 換頁時清掉數量卡篩選、留著房號搜尋；重新整理不經過這裡，所以篩選還在。 */
    document.querySelectorAll('[data-nav]').forEach((a) => a.addEventListener('click', () => { ui.filter = null; saveUi(); }));
    const s = $('#search');
    if (s) { s.value = ui.q; s.addEventListener('input', () => { s.value = s.value.replace(/\D/g, '').slice(0, 4); ui.q = s.value; saveUi(); renderPage(); }); }
  }
  function render() {
    if (view.detail && !Store.byId(view.detail)) view.detail = null;
    const app = $('.app');
    app.classList.toggle('sb-closed', !sbOpen());
    app.classList.toggle('has-detail', !!view.detail);
    $('#sbOpen').hidden = sbOpen();
    $('#connBar').hidden = !view.conn;
    const n = view.load === 'ok' ? triage().length : 0; const b = $('#badge'); b.hidden = !n; b.textContent = n;
    renderDetail(); renderPage(); renderUndo();
  }
  function fault() {
    return '<div class="fault">' + icon('warn') + '<b>讀不到資料</b><span>請檢查網路連線。畫面每 5 秒會自動再試一次。</span><button class="btn-outline" id="retry">重試</button></div>';
  }
  function renderPage() {
    if (PAGE === 'board') renderBoard(); else renderTriage();
    const r = $('#retry'); if (r) r.onclick = () => { view.load = 'ok'; render(); };
  }

  function card(x) {
    const p = pill(x), d = DEPT[x.dept], sub = subOf(x), g = GUESTS[x.room], canDrag = x.state === 'triage';
    return '<button class="card' + (canDrag ? ' draggable' : '') + (view.detail === x.id ? ' selected' : '') + (view.flash[x.id] ? ' flash' : '') + freshAttr(x) + '" data-id="' + x.id + '" aria-label="' + esc(x.room + ' ' + x.title + '，' + tagOf(x)[0] + '，' + p[0]) + '">' +
      '<span class="card-top"><span class="card-room">' + x.room + '</span>' + (g && g.vip ? '<span class="vip">VIP</span>' : '') + '<span class="grow"></span><span class="pill ' + p[1] + '">' + icon('clock', 'xs') + p[0] + '</span></span>' +
      '<span class="card-title clamp2">' + esc(shown(x)) + '</span>' +
      (sub ? '<span class="card-sub clamp2">' + esc(sub) + '</span>' : '') +
      (x.sop && x.state === 'doing' ? '<span class="sop"><span class="bar"><i style="width:' + Math.round(x.sop[0] / x.sop[1] * 100) + '%"></i></span>SOP ' + x.sop[0] + '/' + x.sop[1] + '</span>' : '') +
      '<span class="card-foot"><span class="dot" style="background:' + d.dot + '"></span><span class="dept"><b style="color:' + d.fg + '">' + d.label + '</b><span>· ' + KIND[x.kind] + '</span></span>' +
      '<span class="src"><span class="sr">' + SRC[x.src][0] + '</span>' + icon(SRC[x.src][1]) + '</span>' +
      (x.who ? '<span class="who" style="background:' + d.avBg + ';color:' + d.avFg + '"><span class="sr">負責人 </span>' + esc(x.who.slice(-1)) + '</span>' : '') + '</span></button>';
  }
  /* 代開單送出後這次開出的卡片：落下＋外框光暈，停 2.6 秒。看板每次重畫都會重建卡片，所以用負的動畫延遲接著已經播過的時間，不會從頭重播。 */
  function freshAttr(x) {
    const until = view.fresh[x.id]; if (!until) return '';
    const left = until - Date.now(); if (left <= 0) return '';
    const el = Math.max(0, 2600 - left);
    return ' fresh" data-highlight="created" style="animation-delay:-' + el + 'ms,-' + el + 'ms';
  }
  function sortCol(key, list) {
    if (key === 'triage') return list.sort((p, q) => q.c - p.c);
    if (key === 'pool') return list.sort((p, q) => waitMs(p) - waitMs(q));
    const last = (x) => x.e || x.d || x.s || x.a || x.c;
    return list.sort((p, q) => last(q) - last(p));
  }
  function renderBoard() {
    const host = $('#page'); const all = Store.tasks;
    const openN = all.filter((x) => colOf(x) !== 'closed').length;
    const tri = triage(), pl = pool(), working = all.filter((x) => x.state === 'accepted' || x.state === 'doing');
    const longest = tri.concat(pl).sort((p, q) => waitMs(p) - waitMs(q))[0];
    const wm = longest ? mins(waitMs(longest)) : 0;
    const loading = view.load === 'loading', error = view.load === 'error', blank = loading || error;
    $('#pageSub').textContent = '10/26 週一' + (blank ? '' : ' · ' + openN + ' 張未結案 · 即時更新');
    const num = (n) => (loading ? '<span class="skel line" style="display:block"></span>' : error ? '—' : n);
    const tiles = [
      ['triage', 'tray', '#FFF1C2', '#6B4E00', num(tri.length), '待派單', ''],
      ['pool', 'tray', '#C8E8ED', '#0C7680', num(pl.length), blank ? '待認領' : '待認領（房務 ' + pl.filter((x) => x.dept === hk).length + '・工務 ' + pl.filter((x) => x.dept === eng).length + '）', ''],
      ['doing', 'pulse', '#DDF3E4', '#1A6B34', num(working.length), '處理中（已接＋進行中）', ''],
      ['wait', 'clock', '#FFE4E1', '#B42318', blank ? num('—') : longest ? wm + ' 分' : '—', blank ? '等最久' : longest ? '等最久 · ' + longest.room + ' ' + longest.title : '沒有在等的單', wm >= 20 ? '#B42318' : wm >= 10 ? '#944B00' : '']
    ];
    let keys = COLS.map((c) => c[0]);
    if (ui.filter === 'triage') keys = ['triage']; else if (ui.filter === 'pool') keys = ['pool']; else if (ui.filter === 'doing') keys = ['accepted', 'doing'];
    const list = all.filter((x) => !ui.q || x.room.indexOf(ui.q) === 0);
    const fl = { triage: '只看：待派單', pool: '只看：待認領', doing: '只看：處理中' }[ui.filter] || '';
    const label = fl + (ui.q ? (fl ? ' · ' : '') + '房號 ' + ui.q : '');
    let lanes;
    if (ui.lanes === 'dept') lanes = [[fd, '未分部門', DEPT[fd].dot], [hk, '房務', DEPT[hk].dot], [eng, '工務', DEPT[eng].dot]].map((l) => ({ key: l[0], label: l[1], dot: l[2], items: list.filter((x) => x.dept === l[0]) }));
    else if (ui.lanes === 'floor') lanes = [2, 3, 4, 5].map((f) => ({ key: 'f' + f, label: f + ' 樓', dot: '#8E8E93', items: list.filter((x) => x.floor === f) }));
    else lanes = [{ key: 'all', label: '', items: list }];
    const heads = keys.map((k) => {
      const c = COLS.find((y) => y[0] === k); const inCol = list.filter((x) => colOf(x) === k);
      let warn = '';
      if (k === 'triage' || k === 'pool') { const n = inCol.filter((x) => mins(waitMs(x)) >= 20).length; if (n) warn = '逾 20 分 ' + n; }
      if (k === 'done') { const n = inCol.filter((x) => x.kind === 'cleaning').length; if (n) warn = '待查房 ' + n; }
      return '<div class="col-head"><span class="dot" style="background:' + c[2] + '"></span><span class="col-name">' + c[1] + '</span>' + (blank ? '' : '<span class="count">' + inCol.length + '</span>') + (warn && !blank ? '<span class="col-warn">' + warn + '</span>' : '') + '</div>';
    }).join('');
    let body;
    if (error) body = fault();
    else if (loading) body = '<div class="board" data-n="8"><div class="cols board-heads">' + heads + '</div><div class="cols">' + keys.map(() => '<div class="cell"><div class="skel card-skel"></div><div class="skel card-skel"></div></div>').join('') + '</div></div>';
    else body = '<div class="board" data-n="' + keys.length + '"><div class="cols board-heads">' + heads + '</div>' + lanes.map((ln) =>
      '<div class="lane">' + (ln.label ? '<div class="lane-head"><span class="sq" style="background:' + ln.dot + '"></span><b>' + ln.label + '</b><span>' + ln.items.filter((x) => colOf(x) !== 'closed').length + ' 張未結案</span></div>' : '') +
      '<div class="cols">' + keys.map((k) => { const cards = sortCol(k, ln.items.filter((x) => colOf(x) === k)); return '<div class="cell" data-col="' + k + '" data-lane="' + ln.key + '">' + (cards.length ? cards.map(card).join('') : '<span class="cell-empty">' + COLS.find((y) => y[0] === k)[3] + '</span>') + '</div>'; }).join('') + '</div></div>').join('') + '</div>';
    const old = $('.board-scroll'); const keep = old ? [old.scrollLeft, old.scrollTop] : [0, 0];
    host.innerHTML =
      '<div class="tiles">' + tiles.map((ti) => '<button class="tile" data-tile="' + ti[0] + '"' + (ti[0] !== 'wait' ? ' aria-pressed="' + (ui.filter === ti[0]) + '"' : '') + '><span class="tile-icon" style="background:' + ti[2] + ';color:' + ti[3] + '">' + icon(ti[1]) + '</span><span class="grow"><span class="tile-n"' + (ti[6] ? ' style="color:' + ti[6] + '"' : '') + '>' + ti[4] + '</span><span class="tile-label clamp3">' + esc(ti[5]) + '</span></span></button>').join('') + '</div>' +
      '<div class="controls"><div class="seg" role="group" aria-label="泳道">' + [['none', '不分'], ['dept', '部門'], ['floor', '樓層']].map((o) => '<button data-lane-opt="' + o[0] + '" aria-pressed="' + (ui.lanes === o[0]) + '">' + o[1] + '</button>').join('') + '</div>' +
      (label ? '<button class="filter-pill" id="clearFilter">' + esc(label) + icon('x', 'sm') + '</button>' : '') +
      '<span class="grow"></span><span class="hint" id="hint" role="status">拖曳「待派單」卡片到房務或工務即完成派單；其他卡片只能查看</span></div>' +
      '<div class="board-wrap">' + (error ? body : '<div class="board-scroll">' + body + '</div><div class="more-fade" id="moreFade" hidden></div><button class="more-btn" id="moreBtn" hidden><span></span></button>') + '</div>';
    const sc = $('.board-scroll');
    if (sc) { sizeCols(); sc.scrollLeft = keep[0]; sc.scrollTop = keep[1]; sc.addEventListener('scroll', updateMore); updateMore(); }
    const cf = $('#clearFilter'); if (cf) cf.onclick = () => { ui.filter = null; ui.q = ''; $('#search').value = ''; saveUi(); renderPage(); };
    host.querySelectorAll('[data-lane-opt]').forEach((b) => { b.onclick = () => { ui.lanes = b.dataset.laneOpt; saveUi(); renderPage(); }; });
    host.querySelectorAll('[data-tile]').forEach((b) => { b.onclick = () => {
      if (blank) return;
      if (b.dataset.tile === 'wait') { if (longest) openDetail(longest.id); return; }
      ui.filter = ui.filter === b.dataset.tile ? null : b.dataset.tile; saveUi(); renderPage();
    }; });
    const mb = $('#moreBtn'); if (mb) mb.onclick = () => { sc.scrollBy({ left: sc.querySelector('.col-head').offsetWidth + 10, behavior: 'smooth' }); };
  }
  /* 8 欄都在時平均分掉可用寬度、每欄至少 200px，視窗再寬也不會置中留白；篩選後只剩一兩欄時上限 260px，卡片才不會被拉成一長條。 */
  function sizeCols() {
    const wrap = $('.board-wrap'), b = $('.board'); if (!wrap || !b) return;
    const n = +b.dataset.n; let w = Math.floor((wrap.clientWidth - 10 * (n - 1)) / n);
    w = Math.max(200, n < 8 ? Math.min(260, w) : w);
    b.style.setProperty('--n', n); b.style.setProperty('--colw', w + 'px');
  }
  function updateMore() {
    const sc = $('.board-scroll'), btn = $('#moreBtn'), fade = $('#moreFade'); if (!sc || !btn) return;
    const right = sc.getBoundingClientRect().right;
    let n = 0; sc.querySelectorAll('.col-head').forEach((h) => { if (h.getBoundingClientRect().right > right + 4) n += 1; });
    const end = sc.scrollLeft + sc.clientWidth >= sc.scrollWidth - 8;
    btn.hidden = fade.hidden = !n || end;
    btn.setAttribute('aria-label', '右邊還有 ' + n + ' 個狀態，往右捲動');
    btn.firstChild.innerHTML = '還有 ' + n + ' 個狀態' + icon('chevron', 'xs');
  }

  /* 不用瀏覽器內建的拖放，因為它在觸控螢幕上不會動；改成自己追蹤按住與移動，滑鼠與手指走同一套。
     觸控要先按住 0.25 秒才開始拖，短按才能留給「打開詳情」、直接滑才能留給「捲動看板」。 */
  const drag = { id: null, pending: null, ghost: null, zone: null, timer: null, justDragged: false };
  function dragZones(x) {
    document.querySelectorAll('.cell[data-col="pool"]').forEach((cell) => {
      const lane = cell.dataset.lane; let depts = [];
      if (ui.lanes === 'none') depts = [hk, eng];
      else if (ui.lanes === 'dept') depts = lane === hk ? [hk] : lane === eng ? [eng] : [];
      else if (lane === 'f' + x.floor) depts = [hk, eng];
      if (!depts.length) return;
      cell.classList.add('droppable');
      depts.slice().reverse().forEach((d) => { const z = document.createElement('div'); z.className = 'zone'; z.dataset.dept = d; z.innerHTML = '<b>給' + DEPT[d].label + '</b><span>放進' + DEPT[d].label + '待認領</span>'; cell.prepend(z); });
    });
  }
  /* 拖曳開始時只加樣式與拖放區、不重畫看板：手指按住的那張卡片一旦被換掉，瀏覽器就不再送後續的觸控事件。 */
  function dragStart(el, e) {
    const x = Store.byId(el.dataset.id); if (!x) return;
    drag.id = x.id;
    const r = el.getBoundingClientRect(); drag.dx = e.clientX - r.left; drag.dy = e.clientY - r.top;
    const g = el.cloneNode(true); g.classList.add('drag-ghost'); g.style.width = r.width + 'px'; document.body.appendChild(g); drag.ghost = g;
    el.classList.add('ghosted'); el.closest('.cell').classList.add('origin');
    $('.board').classList.add('is-dragging');
    const h = $('#hint'); h.classList.add('dragging'); h.textContent = '放到「待認領」狀態的房務或工務，就完成派單';
    dragZones(x); dragMove(e);
  }
  function dragMove(e) {
    drag.ghost.style.left = (e.clientX - drag.dx) + 'px'; drag.ghost.style.top = (e.clientY - drag.dy) + 'px';
    const hit = document.elementFromPoint(e.clientX, e.clientY); const z = hit && hit.closest ? hit.closest('.zone') : null;
    if (z !== drag.zone) {
      if (drag.zone) { drag.zone.classList.remove('over'); drag.zone.lastChild.textContent = '放進' + DEPT[drag.zone.dataset.dept].label + '待認領'; }
      if (z) { z.classList.add('over'); z.lastChild.textContent = '放開就完成派單'; }
      drag.zone = z;
    }
    const sc = $('.board-scroll'); const r = sc.getBoundingClientRect();
    if (e.clientX > r.right - 48) sc.scrollLeft += 14; else if (e.clientX < r.left + 48) sc.scrollLeft -= 14;
  }
  function dragEnd(drop) {
    clearTimeout(drag.timer); drag.pending = null;
    if (!drag.id) return;
    const id = drag.id, z = drop ? drag.zone : null;
    if (drag.ghost) drag.ghost.remove();
    drag.id = null; drag.ghost = null; drag.zone = null;
    /* 放開滑鼠後瀏覽器還會補一個點擊事件；短暫忽略它，拖完才不會順便打開詳情面板。 */
    drag.justDragged = true; setTimeout(() => { drag.justDragged = false; }, 60);
    if (z) dispatch(id, z.dataset.dept, 'drag'); else renderPage();
  }
  function bindDrag() {
    document.addEventListener('pointerdown', (e) => {
      const el = e.target.closest ? e.target.closest('.card.draggable') : null;
      if (!el || (e.pointerType === 'mouse' && e.button !== 0)) return;
      drag.pending = { el, x: e.clientX, y: e.clientY, type: e.pointerType };
      if (e.pointerType !== 'mouse') drag.timer = setTimeout(() => { if (drag.pending) { const p = drag.pending; drag.pending = null; dragStart(p.el, { clientX: p.x, clientY: p.y }); } }, 250);
    });
    document.addEventListener('pointermove', (e) => {
      if (drag.id) { dragMove(e); return; }
      const p = drag.pending; if (!p) return;
      const far = Math.hypot(e.clientX - p.x, e.clientY - p.y);
      if (p.type === 'mouse') { if (far > 6) { drag.pending = null; dragStart(p.el, e); } }
      else if (far > 10) { clearTimeout(drag.timer); drag.pending = null; }
    });
    document.addEventListener('pointerup', () => dragEnd(true));
    document.addEventListener('pointercancel', () => { if (!drag.id) { clearTimeout(drag.timer); drag.pending = null; } });
    /* 手指拖著卡片時要擋下瀏覽器自己的捲動，否則看板會跟著手指跑；這個監聽必須宣告成可以取消預設行為才擋得住。 */
    document.addEventListener('touchmove', (e) => { if (drag.id) { e.preventDefault(); dragMove(e.touches[0]); } }, { passive: false });
    document.addEventListener('touchend', () => { if (drag.id) dragEnd(true); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && drag.id) dragEnd(false); });
    document.addEventListener('contextmenu', (e) => { if (e.target.closest && e.target.closest('.card.draggable') && (drag.id || drag.pending)) e.preventDefault(); });
  }

  function renderTriage() {
    const host = $('#page'); const rows = triage();
    if (view.load === 'loading') { $('#pageSub').textContent = ''; host.innerHTML = '<div class="skel row-skel"></div><div class="skel row-skel"></div><div class="skel row-skel"></div>'; return; }
    if (view.load === 'error') { $('#pageSub').textContent = ''; host.innerHTML = fault(); return; }
    if (!rows.length) {
      const no = me().no, a = Store.data.sent[no] || 0, b = Store.data.answered[no] || 0, done = a + b > 0;
      $('#pageSub').textContent = '目前沒有要派的單';
      host.innerHTML = '<div class="fault' + (done ? ' done' : '') + '">' + icon(done ? 'checkCircle' : 'tray') + '<b>' + (done ? '都派完了' : '目前沒有要派的單') + '</b><span>' + (done ? '今天已派單 ' + a + ' 單、當場回答 ' + b + ' 單。新的需求進來會出現在這裡。' : '新的需求進來會出現在這裡。') + '</span></div>';
      return;
    }
    $('#pageSub').textContent = rows.length + ' 張等你派單 · 最久已等 ' + mins(rows[rows.length - 1].c) + ' 分';
    const pl = pool();
    host.innerHTML =
      '<div class="tri-head"><span class="load">房務 ' + pl.filter((x) => x.dept === hk).length + ' 單待接</span><span class="load">工務 ' + pl.filter((x) => x.dept === eng).length + ' 單待接</span><span class="grow"></span><span class="tri-help">點部門就送出，5 秒內可復原；當場回答會先開回覆面板</span></div>' +
      rows.map((x) => { const g = GUESTS[x.room], m = mins(x.c);
        return '<div class="tri-row" data-row="' + x.id + '"><button class="room-tile" aria-label="' + x.room + ' 詳情"><b>' + x.room + '</b><span>' + x.floor + ' 樓</span></button>' +
          '<div class="tri-body"><span class="tri-text">「' + esc(x.title) + '」</span>' + (x.orig ? '<span class="orig">' + x.origLang + '原文：' + esc(x.orig) + '</span>' : '') +
          '<span class="tri-meta"><span>' + (g ? esc(g.name) : '空房') + '</span>' + (g && g.vip ? '<span class="vip">VIP</span>' : '') + '<span>· 住客掃碼 ·</span><span class="wait ' + tone(m) + '">已等 ' + (m < 1 ? '不到 1 分' : m + ' 分') + '</span></span></div>' +
          '<div class="tri-acts" role="group" aria-label="' + x.room + ' 派單"><button class="btn-act" data-act="housekeeping">派給房務</button><button class="btn-act" data-act="engineering">派給工務</button><button class="btn-act" data-act="answer">當場回答…</button></div></div>'; }).join('') +
      '<span class="tri-foot">只有住客打字、系統又猜不準部門的需求會來這裡。勾選單和命中規則的文字會直接進部門待認領。也可以在總覽看板把卡片拖到房務或工務。</span>';
  }

  function openDetail(id) { view.detail = id; render(); }
  function closeDetail() { view.detail = null; render(); }
  function renderDetail() {
    const host = $('#detailHost'); const x = view.detail && Store.byId(view.detail);
    if (!x) { host.innerHTML = ''; host.dataset.shown = ''; return; }
    const g = GUESTS[x.room], tag = tagOf(x), info = infoOf(x), d = DEPT[x.dept];
    const rows = [['房號', x.room + '（' + x.floor + ' 樓）'], ['住客', g ? g.name + ' · ' + g.meta : '空房'], ['部門', d.label + ' · ' + KIND[x.kind]],
      ['負責人', x.who || (x.state === 'pool' ? '等同仁認領' : '—')], ['來源', { guest: '住客掃碼', desk: '櫃檯代開（' + x.by + '）', system: '系統排房', staff: '同仁報修（' + x.by + '）' }[x.src]], ['建立', hm(x.c) + '（' + agoText(x.c) + '）']];
    let bar = '';
    if (x.state === 'triage') bar = '<div class="action-bar" role="group" aria-label="派單"><button data-act="housekeeping">派給房務</button><button data-act="engineering">派給工務</button><button class="solid" data-act="answer">當場回答…</button></div>';
    else if (!x.answered) {
      const mgr = isManager(); let b = [];
      if (x.state === 'pool') b = ['取消', '延後…'].concat(mgr ? ['指派…'] : []);
      else if (['accepted', 'doing', 'interrupted', 'deferred'].indexOf(x.state) >= 0 && mgr) b = ['取消', '延後…', '轉派…'];
      if (b.length) bar = '<div class="action-bar" role="group" aria-label="單據管理">' + b.map((l, i) => '<button data-inert class="' + (i === 0 ? 'danger' : i === 2 ? 'solid' : '') + '">' + l + '</button>').join('') + '</div>';
    }
    const same = host.dataset.shown === x.id; const body = host.querySelector('.detail-body'); const keep = same && body ? body.scrollTop : 0;
    host.innerHTML = '<aside class="detail" aria-label="' + x.id + ' 詳情"' + (same ? ' style="animation:none"' : '') + '>' +
      '<div class="panel-nav"><span><button class="nav-text" id="detailClose">關閉</button></span><h2>' + x.id + '</h2><span></span></div>' +
      '<div class="detail-body">' +
        '<div class="d-card"><div class="d-top"><span class="d-room">' + x.room + '</span>' + (g && g.vip ? '<span class="vip">VIP</span>' : '') + '<span class="grow"></span><span class="st ' + tag[1] + '">' + tag[0] + '</span></div>' +
          '<span class="d-title">' + esc(shown(x)) + '</span>' + (x.orig ? '<span class="d-orig">' + x.origLang + '原文：' + esc(x.orig) + '</span>' : '') +
          '<span class="d-meta">' + (g ? esc(g.name) + ' · ' : '') + KIND[x.kind] + ' · ' + agoText(x.c) + '建立</span></div>' +
        (info ? '<div class="info ' + info[2] + '">' + icon('info') + '<div><b>' + esc(info[0]) + '</b>' + (info[1] ? '<span>' + esc(info[1]) + '</span>' : '') + '</div></div>' : '') +
        (x.sop && x.kind === 'cleaning' && (x.state === 'accepted' || x.state === 'doing') ? '<div class="sop-card"><div>清潔 SOP<span>' + x.sop[0] + ' / ' + x.sop[1] + ' 項</span></div><span class="bar"><i style="width:' + Math.round(x.sop[0] / x.sop[1] * 100) + '%"></i></span></div>' : '') +
        '<span class="sec-label">內容</span><dl class="rows" style="margin:0">' + rows.map((r) => '<div><dt>' + r[0] + '</dt><dd>' + esc(r[1]) + '</dd></div>').join('') + '</dl>' +
        '<span class="sec-label">歷程（住客來電詢問時看這裡）</span><ol class="events">' + eventsOf(x).map((e) => '<li><div class="ev-rail"><i style="background:' + e.c + '"></i><s></s></div><div class="ev-text"><b>' + esc(e.text) + '</b><span>' + hm(e.at) + ' · ' + esc(e.by) + '</span></div></li>').join('') + '</ol>' +
      '</div>' + bar + '</aside>';
    host.dataset.shown = x.id;
    host.querySelector('.detail-body').scrollTop = keep;
    $('#detailClose').onclick = closeDetail;
  }

  const OTHER = '王志明';
  /* 對話框的標題與第一句看「這張單被怎麼處理掉了」，最後一句看「我剛才想做什麼」，四種撞單才不用各寫一套。 */
  function conflictAlert(x, did, after) {
    let title, head;
    if (x.answered) { title = '這單已經回答過了'; head = x.answeredBy + ' 剛剛已經當場回答 ' + x.room + '。'; }
    else if (x.state === 'cancelled') { title = '住客已經收回這張需求'; head = x.room + ' 的住客剛剛自己收回了。'; }
    else { title = '這單已經被派走了'; head = OTHER + ' 剛剛把 ' + x.room + ' 派給' + DEPT[x.dept].label + '。'; }
    showAlert(title, head + '看板已更新成最新狀態，' + (did === 'answer' ? '不需要再回覆。' : '不需要再派單。'), ok(after));
  }
  function applyFail(x) {
    const f = Q.get('fail');
    if (f === 'assigned') { x.state = 'pool'; x.dept = eng; x.log.push({ at: now(), by: OTHER, text: '派給工務，進工務待認領', c: 'desk' }); }
    else if (f === 'answered') { x.state = 'done'; x.answered = 1; x.reply = '早餐 07:00–10:00，在 2 樓自助餐廳，憑房卡入場。'; x.answeredBy = OTHER; x.e = now(); }
    else if (f === 'withdrawn') { x.state = 'cancelled'; x.cancel = 'guest_withdrew'; x.e = now(); }
    else return false;
    Store.save(); return true;
  }
  function dispatch(id, dept, via) {
    const x = Store.byId(id); if (!x) return;
    if (Q.get('fail') === 'net') { render(); showAlert('沒有派成功', x.room + ' 還在待派單。請檢查網路連線後再試一次。', ok()); return; }
    if (applyFail(x) || x.state !== 'triage') { render(); conflictAlert(x, 'assign'); return; }
    const m = me();
    x.state = 'pool'; x.dept = dept;
    x.log.push({ at: now(), by: m.name, text: '派給' + DEPT[dept].label + (via === 'drag' ? '（拖曳）' : '') + '，進' + DEPT[dept].label + '待認領', c: 'desk', undoable: 1 });
    Store.data.sent[m.no] = (Store.data.sent[m.no] || 0) + 1; Store.save();
    view.flash = {}; view.flash[id] = 1;
    setUndo({ id, label: '已派給' + DEPT[dept].label + '（' + x.room + '）', until: Date.now() + 5000 });
    render();
  }
  let undoTimer = null;
  /* 浮條的到期時刻也存進分頁，5 秒內從待派單頁換到總覽看板時浮條才接得上。 */
  function setUndo(u) {
    view.undo = u; clearTimeout(undoTimer);
    if (u) { ss.set('deskUndo', u); undoTimer = setTimeout(() => { setUndo(null); renderUndo(); }, Math.max(0, u.until - Date.now())); } else ss.del('deskUndo');
  }
  function renderUndo() {
    const host = $('#undoHost'); const u = view.undo;
    if (!u) { host.innerHTML = ''; host.dataset.key = ''; return; }
    if (host.dataset.key === u.id + u.until) return;
    host.dataset.key = u.id + u.until;
    host.innerHTML = '<div class="undo" role="status">' + icon('check') + '<span class="grow">' + esc(u.label) + '</span><button id="undoBtn">復原</button></div>';
    $('#undoBtn').onclick = () => undo(u.id);
  }
  function undo(id) {
    const x = Store.byId(id), m = me(), mode = Q.get('undo'), d = DEPT[x.dept].label;
    setUndo(null);
    if (mode === 'net') { render(); showAlert('沒有復原成功', x.room + ' 還在' + d + '待認領。請檢查網路連線；需要收回請直接聯絡' + d + '。', ok()); return; }
    if (mode === 'claimed') { const who = x.dept === eng ? '阿德' : '阿美'; x.state = 'accepted'; x.who = who; x.how = 'claim'; x.a = now(); Store.save(); render(); showAlert('無法復原', who + ' 已經認領 ' + x.room + '，這張單留在' + d + '。', ok()); return; }
    if (mode === 'withdrawn') { x.state = 'cancelled'; x.cancel = 'guest_withdrew'; x.e = now(); Store.save(); render(); showAlert('無法復原', x.room + ' 的住客已經自己收回這張需求，這張單已取消。', ok()); return; }
    x.state = 'triage'; x.dept = fd; x.log = x.log.filter((l) => !l.undoable);
    Store.data.sent[m.no] = Math.max(0, (Store.data.sent[m.no] || 1) - 1); Store.save();
    view.flash = {}; view.flash[id] = 1; render();
  }
  const QUICK = [['早餐時間', '早餐 07:00–10:00，在 2 樓自助餐廳，憑房卡入場。'], ['Wi-Fi', 'Wi-Fi 名稱 Athena-Guest，密碼在房卡套背面。'], ['退房時間', '退房時間是中午 12:00，需要延遲退房請撥 0 找櫃檯。'], ['健身房', '健身房在 6 樓，開放 06:00–22:00，憑房卡進入。'], ['叫車', '已幫您預約明早 07:00 計程車，司機會在大廳等候。']];
  function openAnswer(id) { view.sheet = { id, pick: null }; renderSheet(); const ta = $('#reply'); if (ta) ta.focus(); }
  function sendAnswer() {
    const sh = view.sheet, x = Store.byId(sh.id), text = $('#reply').value.trim(), m = me();
    if (!text) return;
    if (Q.get('fail') === 'net') { showAlert('沒有送出', '回覆還沒送出，' + x.room + ' 還在待派單。請檢查網路連線後再試一次。', ok()); return; }
    if (applyFail(x) || x.state !== 'triage') { render(); conflictAlert(x, 'answer', () => { view.sheet = null; renderSheet(); }); return; }
    x.state = 'done'; x.answered = 1; x.reply = text; x.answeredBy = m.name; x.e = now();
    Store.data.answered[m.no] = (Store.data.answered[m.no] || 0) + 1; Store.save();
    view.sheet = null; view.flash = {}; view.flash[x.id] = 1; renderSheet(); render();
  }

  function renderSheet() {
    const host = $('#sheetHost');
    if (view.sheet) {
      const x = Store.byId(view.sheet.id), g = GUESTS[x.room];
      host.innerHTML = '<div class="scrim" data-close><div class="sheet" role="dialog" aria-label="當場回答 · ' + x.room + '">' +
        '<div class="panel-nav"><span><button class="nav-text" data-close>取消</button></span><h2>當場回答 · ' + x.room + '</h2><span></span></div>' +
        '<div class="sheet-body"><div class="quote"><small>' + x.room + ' · ' + (g ? esc(g.name) + ' · ' : '') + '住客掃碼 · ' + agoText(x.c) + '</small><b>「' + esc(x.title) + '」</b>' + (x.orig ? '<small>' + x.origLang + '原文：' + esc(x.orig) + '</small>' : '') + '</div>' +
          '<div class="stack-8"><span class="label-sm">常用回覆（內容來自住客資訊頁）</span><div class="chips">' + QUICK.map((q, i) => '<button class="chip" data-quick="' + i + '" aria-pressed="false">' + q[0] + '</button>').join('') + '</div></div>' +
          '<label class="reply"><span>回覆住客</span><textarea id="reply" maxlength="120" rows="3" placeholder="例如：早餐 07:00–10:00，在 2 樓自助餐廳"></textarea><span class="counter" id="counter">0 / 120</span></label>' +
          '<span class="label-sm">送出後這單結案，記為「當場回答」，不算部門做掉的單。住客的進度頁會看到這段回覆。</span></div>' +
        '<div class="sheet-foot"><button class="btn-primary" id="sendAnswer" disabled>回覆並結案</button></div></div></div>';
      const ta = $('#reply'), btn = $('#sendAnswer');
      const sync = () => { $('#counter').textContent = ta.value.length + ' / 120'; btn.disabled = !ta.value.trim(); host.querySelectorAll('.chip').forEach((c, i) => c.setAttribute('aria-pressed', String(view.sheet.pick === i))); };
      ta.addEventListener('input', () => { view.sheet.pick = null; sync(); });
      host.querySelectorAll('.chip').forEach((c, i) => { c.onclick = () => { ta.value = QUICK[i][1]; view.sheet.pick = i; sync(); }; });
      btn.onclick = sendAnswer;
    } else if (view.account) {
      const m = me();
      const root = '<div class="id-card"><span class="avatar" aria-hidden="true">' + m.name.slice(-1) + '</span><span class="grow"><b>' + m.name + '</b><span>' + t(ROLE[m.role]) + ' · ' + t('員工編號') + ' ' + m.no + '</span><small>' + HOTEL + '</small></span></div>' +
        '<div class="list"><button id="goLang"><span class="grow">' + t('語言') + '</span><span class="val">' + Lang.name(LANG) + '</span>' + icon('chevron') + '</button></div>' +
        '<div class="list"><button class="logout" id="logout">' + t('登出') + '</button></div>';
      const lang = '<div class="stack-8"><span class="label-sm" style="padding:0 16px">' + t('App 語言') + '</span><div class="list" role="radiogroup" aria-label="' + t('App 語言') + '">' +
        LANGS.map((l) => '<button class="lang-row" role="radio" aria-checked="' + (l[0] === LANG) + '" data-l="' + l[0] + '"><span class="grow">' + l[1] + '<small>' + t(l[2]) + '</small></span>' + (l[0] === LANG ? icon('check') : '') + '</button>').join('') + '</div>' +
        '<span class="label-sm" style="padding:0 16px">' + t('只會改變 App 介面的文字。住客、主管與同事輸入的內容（例如交辦、報修說明）維持原文。') + '</span></div>';
      const isRoot = view.account === 'root';
      host.innerHTML = '<div class="scrim" data-close><div class="sheet account" role="dialog" aria-label="' + t('帳號') + '">' +
        '<div class="panel-nav"><span>' + (isRoot ? '' : '<button class="nav-text" id="acBack">' + icon('back', 'sm') + t('帳號') + '</button>') + '</span><h2>' + t(isRoot ? '帳號' : '語言') + '</h2><span style="justify-self:end"><button class="nav-text strong" data-close>' + t('完成') + '</button></span></div>' +
        '<div class="sheet-body">' + (isRoot ? root : lang) + '</div></div></div>';
      if (isRoot) {
        $('#goLang').onclick = () => { view.account = 'lang'; renderSheet(); };
        $('#logout').onclick = () => showAlert(t('登出？'), t('登出後回到登入頁，下一位同事用自己的帳號登入。還沒送出的代開單、通知草稿不會保留。'), [{ label: t('取消') }, { label: t('登出'), cls: 'danger', fn: () => { Lang.setPage(LANG); location.href = 'index.html'; } }]);
      } else {
        $('#acBack').onclick = () => { view.account = 'root'; renderSheet(); };
        host.querySelectorAll('[data-l]').forEach((b) => { b.onclick = () => { LANG = b.dataset.l; Lang.setUser(m.no, LANG); renderSheet(); }; });
      }
    } else host.innerHTML = '';
  }

  function bindApp() {
    $('.app').addEventListener('click', (e) => {
      if (drag.justDragged) return;
      const el = e.target;
      /* 通知板、遺失物、設定、代開單與單據管理的按鈕屬於之後的版本：照原型畫出來，但這一版點了不能有任何反應。 */
      if (el.closest('[data-inert]')) return;
      if (el.hasAttribute('data-close') || el.closest('button[data-close]')) { view.sheet = null; view.account = null; renderSheet(); return; }
      const act = el.closest('[data-act]');
      if (act) {
        const row = act.closest('[data-row]'); const id = row ? row.dataset.row : view.detail;
        if (act.dataset.act === 'answer') openAnswer(id); else dispatch(id, act.dataset.act, 'button');
        return;
      }
      const c = el.closest('.card'); if (c) { openDetail(c.dataset.id); return; }
      const row = el.closest('[data-row]'); if (row) openDetail(row.dataset.row);
    });
    document.addEventListener('keydown', (e) => {
      if (e.key !== 'Escape' || drag.id || $('#alert') || document.querySelector('.cr-scrim')) return;
      if (view.sheet || view.account) { view.sheet = null; view.account = null; renderSheet(); } else if (view.detail) closeDetail();
    });
    window.addEventListener('resize', () => { sizeCols(); updateMore(); });
  }
  /* 讓 reviewer 不用另開一台裝置也看得到「現場一有變化畫面就跟上」：每 4 秒替住客、同仁、主管各做一步。 */
  function liveDemo() {
    const steps = [
      () => { Store.tasks.push({ id: 'T-1058', room: '316', floor: 3, kind: 'other', dept: fd, src: 'guest', state: 'triage', free: 1, title: '請問泳池開到幾點？', c: now(), log: [] }); return 'T-1058'; },
      () => { const x = Store.byId('T-1040'); x.state = 'accepted'; x.who = '阿美'; x.how = 'claim'; x.a = now(); return x.id; },
      () => { const x = Store.byId('T-1040'); x.state = 'doing'; x.s = now(); return x.id; },
      () => { const x = Store.byId('T-1040'); x.state = 'done'; x.d = now(); return x.id; },
      () => { const x = Store.byId('T-1010'); x.state = 'inspected'; x.inspection = 'passed'; x.inspector = '張主管'; x.e = now(); return x.id; }
    ];
    let i = 0;
    const tick = () => { if (i >= steps.length) return; const id = steps[i++](); view.flash = {}; view.flash[id] = 1; render(); setTimeout(tick, 4000); };
    setTimeout(tick, 4000);
  }
  function initApp() {
    const s = session();
    if (!s) { location.replace('index.html'); return; }
    if (s.mustChange) { location.replace('change-password.html'); return; }
    LANG = Lang.forUser(s.no);
    Store.load();
    if (LOADMODE === 'loading' || LOADMODE === 'error') view.load = LOADMODE;
    const c = Q.get('conn'); view.conn = c === 'down' || c === 'recover';
    if (c === 'recover') setTimeout(() => { view.conn = false; render(); }, 8000);
    const u = ss.get('deskUndo', null); if (u && u.until > Date.now() && Store.byId(u.id)) setUndo(u);
    renderShell(); bindApp(); if (PAGE === 'board') bindDrag();
    const task = Q.get('task'); if (task && Store.byId(task)) view.detail = task;
    render();
    const ans = Q.get('answer'); if (ans && Store.byId(ans) && Store.byId(ans).state === 'triage') openAnswer(ans);
    if (Q.has('live')) liveDemo();
    /* 等待分鐘數不靠後端推送也要往前走，所以每 10 秒重算一次；拖曳中不重畫，免得打斷手上的卡片。 */
    setInterval(() => { if (!drag.id && !drag.pending && view.load === 'ok') { view.flash = {}; renderPage(); if (view.detail) renderDetail(); } }, 10000);
  }

  /* 代開單面板（create-request.js）用的掛點：讀房間清單、住客與看板上的單，送出成功後把新卡片放進看板、清篩選、換到總覽看板並高亮。 */
  let freshTimer = null;
  function showFresh(ids, until) {
    view.fresh = {}; ids.forEach((id) => { view.fresh[id] = until; });
    clearTimeout(freshTimer);
    freshTimer = setTimeout(() => { view.fresh = {}; renderPage(); }, Math.max(0, until - Date.now()));
  }
  window.DeskDemo = {
    Q, esc, now, mins, GUESTS, ROOMS, DEPT, tagOf, showAlert, ok, me,
    tasks: () => Store.tasks,
    detailRoom() { const x = view.detail && Store.byId(view.detail); return x ? x.room : ''; },
    nextId(prefix) { const ns = Store.tasks.filter((x) => x.id.indexOf(prefix) === 0).map((x) => +x.id.slice(2)); const n = Math.max.apply(null, ns.concat([prefix === 'W-' ? 150 : 1057])) + 1; return prefix + String(n).padStart(4, '0'); },
    add(list) { list.forEach((x) => Store.tasks.push(Object.assign({ floor: +x.room[0], log: [] }, x))); Store.save(); },
    afterCreate(ids) {
      ui.filter = null; ui.q = ''; saveUi(); view.detail = null;
      const until = Date.now() + 2600;
      if (PAGE !== 'board') { ss.set('deskFresh', { ids, until }); location.href = 'board.html'; return; }
      const s = $('#search'); if (s) s.value = '';
      const sc = $('.board-scroll'); if (sc) { sc.scrollLeft = 0; sc.scrollTop = 0; }
      showFresh(ids, until); render();
      const first = document.querySelector('.card[data-id="' + ids[0] + '"]'); if (first) first.focus({ preventScroll: true });
    },
    refresh() { render(); },
    logout() { ss.del('deskSession'); location.href = 'index.html'; }
  };
  if (PAGE === 'board') { const f = ss.get('deskFresh', null); ss.del('deskFresh'); if (f && f.until > Date.now()) showFresh(f.ids, f.until); }
  window.addEventListener('DOMContentLoaded', () => {
    const f = Object.keys(view.fresh)[0]; if (!f) return;
    const c = document.querySelector('.card[data-id="' + f + '"]'); if (c) c.focus({ preventScroll: true });
  });

  if (PAGE === 'login') initLogin(); else if (PAGE === 'password') initPassword(); else initApp();
})();
