(function () {
  // 指令區塊的「複製」按鈕
  function selectText(el) {
    var range = document.createRange();
    range.selectNodeContents(el);
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
  }
  document.querySelectorAll('.cmd').forEach(function (box) {
    var pre = box.querySelector('pre');
    if (!pre) return;
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'copy';
    btn.textContent = '複製';
    btn.setAttribute('aria-label', '複製這段指令');
    btn.addEventListener('click', function () {
      var text = pre.innerText;
      var done = function () { btn.textContent = '已複製'; setTimeout(function () { btn.textContent = '複製'; }, 1600); };
      var fallback = function () { selectText(pre); btn.textContent = '已選取，請按 ⌘C'; setTimeout(function () { btn.textContent = '複製'; }, 2400); };
      try {
        if (!navigator.clipboard || !navigator.clipboard.writeText) { fallback(); return; }
        navigator.clipboard.writeText(text).then(done).catch(fallback);
      } catch (e) { fallback(); }
    });
    box.appendChild(btn);
  });

  // 頁內分頁：JS 啟用時才收成分頁；沒有 JS 時全部面板照常顯示
  document.querySelectorAll('.tabs').forEach(function (tabs) {
    var panels = Array.prototype.slice.call(tabs.querySelectorAll(':scope > .tabpanel'));
    if (!panels.length) return;
    var list = tabs.querySelector(':scope > .tablist');
    var buttons = panels.map(function (panel, i) {
      var b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('role', 'tab');
      b.id = panel.id + '-tab';
      b.setAttribute('aria-controls', panel.id);
      b.textContent = panel.getAttribute('data-tab');
      panel.setAttribute('role', 'tabpanel');
      panel.setAttribute('aria-labelledby', b.id);
      panel.setAttribute('tabindex', '0');
      list.appendChild(b);
      return b;
    });
    function show(i, focus) {
      buttons.forEach(function (b, j) {
        var on = i === j;
        b.setAttribute('aria-selected', on ? 'true' : 'false');
        b.tabIndex = on ? 0 : -1;
        panels[j].hidden = !on;
      });
      if (focus) buttons[i].focus();
    }
    buttons.forEach(function (b, i) {
      b.addEventListener('click', function () {
        show(i, false);
        try { history.replaceState(null, '', '#' + panels[i].id); } catch (e) {}
      });
      b.addEventListener('keydown', function (e) {
        var n = buttons.length, k = null;
        if (e.key === 'ArrowRight') k = (i + 1) % n;
        else if (e.key === 'ArrowLeft') k = (i - 1 + n) % n;
        else if (e.key === 'Home') k = 0;
        else if (e.key === 'End') k = n - 1;
        if (k !== null) { e.preventDefault(); show(k, true); }
      });
    });
    list.setAttribute('role', 'tablist');
    tabs.classList.add('js');
    var start = 0;
    panels.forEach(function (p, i) { if (location.hash === '#' + p.id) start = i; });
    show(start, false);
  });
})();
