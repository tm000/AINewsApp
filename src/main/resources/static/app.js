(function () {
  'use strict';

  const toast = document.getElementById('toast');
  function showToast(msg) {
    toast.textContent = msg;
    toast.hidden = false;
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => { toast.hidden = true; }, 3500);
  }

  // ---- 今すぐ更新 ----
  const refresh = document.getElementById('refresh');
  if (refresh) {
    refresh.addEventListener('click', async () => {
      refresh.disabled = true;
      refresh.textContent = '更新中…';
      try {
        const res = await fetch('/api/crawl', { method: 'POST' });
        if (res.status === 429) {
          const data = await res.json().catch(() => ({}));
          showToast(data.message || 'しばらくしてからもう一度お試しください');
          refresh.disabled = false;
          refresh.textContent = '今すぐ更新';
          return;
        }
        for (let i = 0; i < 60; i++) {
          await new Promise(r => setTimeout(r, 3000));
          const s = await (await fetch('/api/status')).json();
          if (!s.crawling) break;
        }
        location.reload();
      } catch (e) {
        showToast('更新に失敗しました');
        refresh.disabled = false;
        refresh.textContent = '今すぐ更新';
      }
    });
  }

  // ---- 翻訳 ----
  function setLang(card, ja) {
    const has = card.dataset.translated === 'true';
    const showJa = ja && has;
    card.querySelectorAll('.t-orig, .s-orig').forEach(el => { el.hidden = showJa; });
    card.querySelectorAll('.t-ja, .s-ja').forEach(el => { el.hidden = !showJa || !el.textContent.trim(); });
    // 要約の訳が無いときは原文の要約を残す
    const sJa = card.querySelector('.s-ja');
    const sOrig = card.querySelector('.s-orig');
    if (showJa && sJa && sOrig && !sJa.textContent.trim()) sOrig.hidden = false;
    card.dataset.showing = showJa ? 'ja' : 'orig';
    // タイトルだけ自動翻訳済みで要約が未翻訳のときは「要約も翻訳」を出す
    const sumBtn = card.querySelector('.translate-summary');
    if (sumBtn) sumBtn.hidden = !(showJa && sJa && !sJa.textContent.trim());
    const btn = card.querySelector('.translate');
    if (btn) btn.textContent = showJa ? '原文を表示' : '日本語に翻訳';
  }

  const serverOn = document.body.dataset.serverTranslation === 'true';
  const local = window.LocalTranslator;
  function useLocal() { return !!(local && local.isEnabled()); }
  function engineOn() { return useLocal() || serverOn; }

  function origText(card, sel) {
    const el = card.querySelector(sel);
    return el ? el.textContent : '';
  }

  function fill(card, data) {
    if (data.titleJa) card.querySelector('.t-ja').textContent = data.titleJa;
    const sJa = card.querySelector('.s-ja');
    if (sJa && data.summaryJa) sJa.textContent = data.summaryJa;
    card.dataset.translated = card.querySelector('.t-ja').textContent.trim() ? 'true' : 'false';
  }

  // 翻訳を 1 件取得する。スマホ内翻訳がオンならそれを使い、オフならサーバーに頼む
  async function fetchTranslation(card, withSummary) {
    if (useLocal()) {
      if (!local.isReady()) showToast('翻訳モデルを準備しています…');
      return local.translateArticle(card.dataset.id, origText(card, '.t-orig'), origText(card, '.s-orig'), withSummary);
    }
    const res = await fetch('/api/articles/' + card.dataset.id + '/translate', { method: 'POST' });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || res.status);
    return data;
  }

  async function translate(card) {
    if (card.dataset.translated === 'true') { setLang(card, true); return true; }
    const btn = card.querySelector('.translate');
    if (btn) { btn.disabled = true; btn.textContent = '翻訳中…'; }
    try {
      fill(card, await fetchTranslation(card, false));
      setLang(card, true);
      return true;
    } catch (e) {
      const msg = String((e && e.message) || e);
      showToast(useLocal() ? 'スマホ内翻訳に失敗しました: ' + msg + '（「スマホ内翻訳」の診断情報を確認）' : msg);
      if (auto && auto.checked && (useLocal() || msg.includes('上限'))) { auto.checked = false; writePref(false); }
      if (btn) btn.textContent = '日本語に翻訳';
      return false;
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  const cards = Array.from(document.querySelectorAll('.card[data-foreign="true"]'));
  cards.forEach(card => {
    const btn = card.querySelector('.translate');
    if (!btn) return;
    btn.addEventListener('click', () => {
      if (card.dataset.showing === 'ja') setLang(card, false); else translate(card);
    });
  });

  cards.forEach(card => {
    const sumBtn = card.querySelector('.translate-summary');
    if (!sumBtn) return;
    sumBtn.addEventListener('click', async () => {
      sumBtn.disabled = true;
      sumBtn.textContent = '翻訳中…';
      try {
        const data = await fetchTranslation(card, true);
        fill(card, data);
        card.querySelector('.s-ja').textContent = data.summaryJa || '';
        setLang(card, true);
      } catch (e) {
        showToast(String((e && e.message) || e));
      } finally {
        sumBtn.disabled = false;
        sumBtn.textContent = '要約も翻訳';
      }
    });
  });

  // 翻訳手段が何も無いときは翻訳ボタンを隠す
  function updateControls() {
    const on = engineOn();
    document.querySelectorAll('.card .translate').forEach(b => { b.hidden = !on; });
    if (!on) document.querySelectorAll('.card .translate-summary').forEach(b => { b.hidden = true; });
    const label = document.getElementById('auto-translate-label');
    if (label) label.hidden = !on;
  }

  // スマホ内翻訳で以前に訳したものは端末に残っているので、すぐ反映する
  function applyLocalCache() {
    if (!useLocal()) return;
    cards.filter(c => c.dataset.translated !== 'true').forEach(c => {
      const data = local.cached(c.dataset.id);
      if (data && data.titleJa) fill(c, data);
    });
  }

  const auto = document.getElementById('auto-translate');
  const KEY = 'ainews.autoTranslate';
  // 保存された設定が無ければ、サーバーが自動翻訳しているときは日本語表示を既定にする
  function readPref() {
    const fallback = !!auto && auto.dataset.defaultOn === 'true';
    try {
      const v = localStorage.getItem(KEY);
      return v === null ? fallback : v === '1';
    } catch (e) { return fallback; }
  }
  function writePref(v) { try { localStorage.setItem(KEY, v ? '1' : '0'); } catch (e) { /* 保存できなくても動作は続ける */ } }

  async function applyAuto(on) {
    if (!on) { cards.forEach(c => setLang(c, false)); return; }
    cards.filter(c => c.dataset.translated === 'true').forEach(c => setLang(c, true));
    // 未翻訳のものは 1 件ずつ順番に（無料 API の負荷を抑えるため）
    for (const c of cards.filter(c => c.dataset.translated !== 'true')) {
      if (!auto.checked) break;
      if (!(await translate(c))) break;
    }
  }

  applyLocalCache();
  updateControls();

  if (auto) {
    auto.checked = readPref() && engineOn();
    auto.addEventListener('change', () => { writePref(auto.checked); applyAuto(auto.checked); });
    applyAuto(auto.checked);
  }

  // ---- スマホ内翻訳（PLaMo）の設定 ----
  const dialog = document.getElementById('plamo-dialog');
  const openBtn = document.getElementById('plamo-open');
  if (dialog && openBtn) {
    const status = document.getElementById('plamo-status');
    const progress = document.getElementById('plamo-progress');
    const enableBtn = document.getElementById('plamo-enable');
    const disableBtn = document.getElementById('plamo-disable');
    const deleteBtn = document.getElementById('plamo-delete');
    let busy = false;

    if (!local || !local.supported()) {
      openBtn.hidden = true;
    }

    async function refreshStatus() {
      const on = useLocal();
      const saved = await local.isDownloaded();
      if (!busy) {
        if (on && local.isReady()) status.textContent = 'オン（準備完了）';
        else if (on) status.textContent = saved ? 'オン（モデルは端末に保存済み）' : 'オン（モデルは未ダウンロード）';
        else status.textContent = saved ? 'オフ（モデルは端末に保存済み）' : 'オフ';
      }
      enableBtn.hidden = on;
      disableBtn.hidden = !on;
      deleteBtn.hidden = !saved || busy;
    }

    const diagBox = document.getElementById('plamo-diag-box');
    const diagPre = document.getElementById('plamo-diag');
    function showDiag(text) {
      if (!diagBox || !text) return;
      diagPre.textContent = text;
      diagBox.hidden = false;
    }
    if (local) {
      local.onDiagnostics = showDiag;
      if (typeof local.diagnostics === 'function') showDiag(local.diagnostics());
    }

    if (local) {
      local.onProgress = (loaded, total) => {
        if (!total || local.isReady() || loaded >= total) return;
        const pct = Math.floor(loaded / total * 100);
        progress.hidden = false;
        progress.value = pct;
        status.textContent = 'モデルをダウンロード中… ' + pct + '%（' + Math.round(loaded / 1048576) + ' / ' + Math.round(total / 1048576) + ' MB）';
        if (!dialog.open) showToast('翻訳モデルをダウンロード中… ' + pct + '%');
      };
    }

    openBtn.addEventListener('click', () => {
      if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
      refreshStatus();
    });
    document.getElementById('plamo-close').addEventListener('click', () => dialog.close());

    enableBtn.addEventListener('click', async () => {
      local.setEnabled(true);
      busy = true;
      enableBtn.disabled = true;
      status.textContent = 'モデルを準備しています…（動作確認に 1 分ほどかかることがあります）';
      updateControls();
      refreshStatus();
      try {
        await local.ensureReady();
        busy = false;
        progress.hidden = true;
        applyLocalCache();
        if (auto) { auto.checked = true; writePref(true); applyAuto(true); }
        await refreshStatus();
        showToast('スマホ内翻訳の準備ができました');
      } catch (e) {
        busy = false;
        progress.hidden = true;
        local.setEnabled(false);
        updateControls();
        await refreshStatus();
        status.textContent = '準備に失敗しました: ' + String((e && e.message) || e);
        if (diagBox && !diagBox.hidden) diagBox.open = true;
      } finally {
        enableBtn.disabled = false;
      }
    });

    disableBtn.addEventListener('click', async () => {
      local.setEnabled(false);
      if (auto && !serverOn) { auto.checked = false; }
      updateControls();
      await refreshStatus();
    });

    deleteBtn.addEventListener('click', async () => {
      deleteBtn.disabled = true;
      try {
        await local.deleteModel();
        local.setEnabled(false);
        updateControls();
        showToast('端末からモデルを削除しました');
      } catch (e) {
        showToast('削除できませんでした: ' + String((e && e.message) || e));
      } finally {
        deleteBtn.disabled = false;
        await refreshStatus();
      }
    });
  }
})();
