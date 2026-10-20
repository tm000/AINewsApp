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
        await fetch('/api/crawl', { method: 'POST' });
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
    const btn = card.querySelector('.translate');
    if (btn) btn.textContent = showJa ? '原文を表示' : '日本語に翻訳';
  }

  async function translate(card) {
    if (card.dataset.translated === 'true') { setLang(card, true); return true; }
    const btn = card.querySelector('.translate');
    if (btn) { btn.disabled = true; btn.textContent = '翻訳中…'; }
    try {
      const res = await fetch('/api/articles/' + card.dataset.id + '/translate', { method: 'POST' });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || res.status);
      card.querySelector('.t-ja').textContent = data.titleJa || '';
      const sJa = card.querySelector('.s-ja');
      if (sJa) sJa.textContent = data.summaryJa || '';
      card.dataset.translated = data.titleJa ? 'true' : 'false';
      setLang(card, true);
      return true;
    } catch (e) {
      showToast(String(e.message || e));
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

  const auto = document.getElementById('auto-translate');
  const KEY = 'ainews.autoTranslate';
  function readPref() { try { return localStorage.getItem(KEY) === '1'; } catch (e) { return false; } }
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

  if (auto) {
    auto.checked = readPref();
    auto.addEventListener('change', () => { writePref(auto.checked); applyAuto(auto.checked); });
    applyAuto(auto.checked);
  }
})();
