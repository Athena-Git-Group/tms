/**
 * TMS POC — 共用渲染元件
 *
 * 刻意不用任何框架：純 DOM API 組 element，讓四個角色頁可以共用同一套
 * 「任務卡 / 九態徽章 / Toast / 經過時間」渲染邏輯，不必各寫一套還互相長不一樣。
 */
(function (global) {
  'use strict';

  var E = global.TMS_ENGINE;

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function stateBadge(state) {
    var b = el('span', 'badge badge-' + state);
    b.appendChild(el('span', 'badge-dot'));
    b.appendChild(el('span', 'badge-label', E.STATE_LABELS[state] || state));
    b.appendChild(el('code', 'badge-enum', state));
    return b;
  }

  function deptTag(dept) {
    if (!dept) return el('span', 'tag tag-muted', '未分類');
    return el('span', 'tag tag-dept-' + dept, E.DEPT_LABELS[dept] || dept);
  }

  function typeTag(type) {
    return el('span', 'tag tag-type', E.TYPE_LABELS[type] || type);
  }

  // 經過時間只在渲染當下算一次；分頁要讓它跳動，自己掛 setInterval 定期重繪即可，
  // 這裡不內建 timer，避免大量任務卡同時各自開 interval 拖垮 POC 的流暢度。
  function elapsedLabel(ts, nowTs) {
    var ms = (nowTs || Date.now()) - ts;
    var min = Math.floor(ms / 60000);
    if (min < 1) return '剛剛';
    if (min < 60) return '已過 ' + min + ' 分鐘';
    var h = Math.floor(min / 60);
    var m = min % 60;
    return '已過 ' + h + ' 小時' + (m > 0 ? m + ' 分' : '');
  }

  var toastHost = null;
  function ensureToastHost() {
    if (toastHost && document.body.contains(toastHost)) return toastHost;
    toastHost = el('div', 'tms-toast-host');
    document.body.appendChild(toastHost);
    return toastHost;
  }

  // variant: info | success | error | warning —— 對應規格要求的三個錯誤態
  // （already_taken / forbidden_self_inspect / invalid_transition）都走 error。
  function toast(message, variant) {
    var host = ensureToastHost();
    var t = el('div', 'tms-toast tms-toast-' + (variant || 'info'), message);
    host.appendChild(t);
    requestAnimationFrame(function () { t.classList.add('is-visible'); });
    setTimeout(function () {
      t.classList.remove('is-visible');
      setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 250);
    }, 3800);
  }

  // 把引擎回傳的 {ok:false, code, message} 統一轉成 toast，三種錯誤態共用同一條路徑，
  // 頁面程式碼不用在每個呼叫點各自判斷字串。
  function toastFromResult(result) {
    if (!result || result.ok) return;
    toast(result.message || '操作失敗', 'error');
  }

  function starRow(current, onRate) {
    var wrap = el('div', 'star-row');
    for (var i = 1; i <= 5; i++) {
      (function (n) {
        var s = el('button', 'star' + (current && n <= current ? ' is-filled' : ''), '★');
        s.type = 'button';
        s.setAttribute('aria-label', n + ' 星');
        if (onRate) s.addEventListener('click', function () { onRate(n); });
        else s.disabled = true;
        wrap.appendChild(s);
      })(i);
    }
    return wrap;
  }

  /**
   * 任務卡：四個角色頁共用的最小單位。
   * opts = {
   *   showRoom, showAssignee, staffName, actions: [{label, variant, onClick, disabled}],
   *   meta: 額外要顯示的一行文字（例如 ruleHit、redoCount）
   * }
   */
  function taskCard(task, opts) {
    opts = opts || {};
    var card = el('article', 'task-card task-card-' + task.state);

    var head = el('div', 'task-card-head');
    if (opts.showRoom !== false) head.appendChild(el('span', 'task-room', task.roomNo + ' 號房'));
    head.appendChild(typeTag(task.type));
    head.appendChild(stateBadge(task.state));
    card.appendChild(head);

    var title = el('h3', 'task-title', task.title);
    card.appendChild(title);

    if (task.detail) {
      var detail = el('p', 'task-detail');
      detail.textContent = task.detail;
      card.appendChild(detail);
    }

    var metaRow = el('div', 'task-meta-row');
    metaRow.appendChild(deptTag(task.dept));
    if (task.ruleHit) metaRow.appendChild(el('span', 'tag tag-rule', '規則引擎命中：' + task.ruleHit));
    if (task.redoCount) metaRow.appendChild(el('span', 'tag tag-warning', '已退回重掃 ' + task.redoCount + ' 次'));
    if (opts.showAssignee && task.assigneeId) {
      metaRow.appendChild(el('span', 'tag tag-assignee', '負責人：' + (opts.staffName ? opts.staffName(task.assigneeId) : task.assigneeId)));
    }
    metaRow.appendChild(el('span', 'tag tag-time', elapsedLabel(task.updatedAt)));
    // 時限標記只做提示不做自動升級（規格：v1 無 SLA 升級鏈）；打掃預設不掛時限，
    // 免得種子資料一重置就滿版逾時，蓋掉真正該被看到的臨時需求。
    var sla = (E.getSettings().slaByType || {})[task.type];
    if (sla && ['triage', 'pool', 'accepted', 'doing'].indexOf(task.state) !== -1) {
      var elapsedMin = Math.floor((Date.now() - task.createdAt) / 60000);
      metaRow.appendChild(el('span', elapsedMin > sla ? 'tag tag-warning' : 'tag tag-time',
        elapsedMin > sla ? '逾時（限 ' + sla + ' 分）' : '時限 ' + sla + ' 分'));
    }
    card.appendChild(metaRow);

    if (opts.meta) card.appendChild(el('p', 'task-extra-meta', opts.meta));

    if (opts.actions && opts.actions.length) {
      var actions = el('div', 'task-actions');
      opts.actions.forEach(function (a) {
        var btn = el('button', 'btn btn-' + (a.variant || 'secondary'), a.label);
        btn.type = 'button';
        if (a.disabled) btn.disabled = true;
        btn.addEventListener('click', a.onClick);
        actions.appendChild(btn);
      });
      card.appendChild(actions);
    }

    return card;
  }

  function emptyState(message) {
    return el('p', 'empty-state', message);
  }

  // 進狀態前一律壓縮圖：模擬引擎整份狀態受 localStorage ~5MB 上限約束，
  // 原圖幾張就會讓所有頁面一起掛掉；正式版照片走物件儲存，資料庫只存連結。
  function compressImage(file, maxSize, cb) {
    var reader = new FileReader();
    reader.onload = function () {
      var img = new Image();
      img.onload = function () {
        var scale = Math.min(1, maxSize / Math.max(img.width, img.height));
        var canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
        cb(canvas.toDataURL('image/jpeg', 0.75));
      };
      img.onerror = function () { cb(null); };
      img.src = reader.result;
    };
    reader.onerror = function () { cb(null); };
    reader.readAsDataURL(file);
  }

  function showPhoto(dataUrl) {
    var overlay = el('div', 'tms-photo-overlay');
    var img = document.createElement('img');
    img.src = dataUrl;
    img.alt = '遺失物照片（點擊關閉）';
    overlay.appendChild(img);
    overlay.addEventListener('click', function () { overlay.remove(); });
    document.body.appendChild(overlay);
  }

  function photoThumb(dataUrl) {
    var img = document.createElement('img');
    img.className = 'lf-thumb';
    img.src = dataUrl;
    img.alt = '遺失物照片（點擊放大）';
    img.addEventListener('click', function () { showPhoto(dataUrl); });
    return img;
  }

  global.TMS_COMPONENTS = {
    el: el,
    stateBadge: stateBadge,
    deptTag: deptTag,
    typeTag: typeTag,
    elapsedLabel: elapsedLabel,
    toast: toast,
    toastFromResult: toastFromResult,
    starRow: starRow,
    taskCard: taskCard,
    emptyState: emptyState,
    compressImage: compressImage,
    showPhoto: showPhoto,
    photoThumb: photoThumb
  };
})(window);
