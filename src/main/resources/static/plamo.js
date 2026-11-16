// スマホ内（ブラウザ内）で PLaMo 3 Translate 0.8B を動かして翻訳する。
// 実行エンジンは wllama（llama.cpp の WebAssembly 版、WebGPU があれば GPU も使う）。
// モデルは初回だけ Hugging Face から取得し、ブラウザの保存領域に置いて次回から再利用する。
(function () {
  'use strict';

  const MODEL = { repo: 'pfnet/plamo-3-translate-0.8b-GGUF', quant: 'Q4_0' }; // 配布元が「スマホ向け」としている量子化
  const MODEL_SIZE_LABEL = '約550MB';
  const PREF_KEY = 'ainews.plamo';
  const CACHE_PREFIX = 'ainews.plamo.ja.';
  const URL_KEY = 'ainews.plamo.url';
  const STOP = ['<|plamo:tag|>', '<|plamo:bos|>'];

  let wllama = null;
  let loading = null;
  let queue = Promise.resolve();

  function readPref() { try { return localStorage.getItem(PREF_KEY) === '1'; } catch (e) { return false; } }
  function writePref(v) { try { localStorage.setItem(PREF_KEY, v ? '1' : '0'); } catch (e) { /* 保存できなくても続ける */ } }

  function supported() {
    return typeof WebAssembly === 'object' && typeof Worker === 'function' && typeof fetch === 'function';
  }

  // 取得元 URL は一度調べたら覚えておく（2 回目以降は電波が無くても読み込めるように）
  async function modelUrl() {
    try { const u = localStorage.getItem(URL_KEY); if (u) return u; } catch (e) { /* 続ける */ }
    const mod = await import('/vendor/wllama/index.js');
    const src = await mod.getHFModelSource(MODEL);
    try { localStorage.setItem(URL_KEY, src.url); } catch (e) { /* 続ける */ }
    return src.url;
  }

  async function createInstance() {
    const mod = await import('/vendor/wllama/index.js');
    return new mod.Wllama({ default: '/vendor/wllama/wllama.wasm' }, {
      allowOffline: true,
      suppressNativeLog: true,
      logger: mod.LoggerWithoutDebug,
    });
  }

  // ---- 動かし方（端末によって動く組み合わせが違うので、試して動いたものを覚える） ----
  // gpu: WebGPU を使うか / chat: モデル内蔵のテンプレートを使うか（false は PFML3 を直接書く）
  const MODES = [
    { gpu: false, chat: false },
    { gpu: false, chat: true },
    { gpu: true, chat: false },
    { gpu: true, chat: true },
  ];
  const MODE_KEY = 'ainews.plamo.mode';
  const TIMEOUT_MS = 90000;
  let mode = null;        // 動作確認できた組み合わせ
  let loadedGpu = null;   // いま読み込んでいるモデルが GPU を使っているか
  const diag = [];        // 画面に出す診断メモ

  function note(msg) {
    diag.push(new Date().toLocaleTimeString() + ' ' + msg);
    if (diag.length > 30) diag.shift();
    const api = window.LocalTranslator;
    if (api && typeof api.onDiagnostics === 'function') api.onDiagnostics(diag.join('\n'));
  }
  function modeName(m) { return (m.gpu ? 'GPU' : 'CPU') + '/' + (m.chat ? 'chat' : 'raw'); }
  function errText(e) { return String((e && e.message) || e).slice(0, 200); }
  function hasJapanese(t) { return /[\u3040-\u30ff\u4e00-\u9fff]/.test(t || ''); }

  function readMode() {
    try {
      const m = JSON.parse(localStorage.getItem(MODE_KEY) || 'null');
      return m && typeof m.gpu === 'boolean' && typeof m.chat === 'boolean' ? m : null;
    } catch (e) { return null; }
  }
  function writeMode(m) { try { localStorage.setItem(MODE_KEY, JSON.stringify(m)); } catch (e) { /* 続ける */ } }

  async function unload() {
    const w = wllama;
    wllama = null;
    loadedGpu = null;
    if (w) { try { await w.exit(); } catch (e) { /* 何もしない */ } }
  }

  /** モデルを読み込む（未ダウンロードなら取得する）。gpu を変えるときは読み込み直す。 */
  async function load(gpu) {
    if (wllama && wllama.isModelLoaded() && loadedGpu === gpu) return;
    await unload();
    const progressCallback = ({ loaded, total }) => {
      const api = window.LocalTranslator;
      if (api && typeof api.onProgress === 'function') api.onProgress(loaded, total);
    };
    const params = { n_ctx: 2048, n_batch: 512, jinja: true, progressCallback };
    if (!gpu) params.n_gpu_layers = 0;
    const url = await modelUrl();
    const w = await createInstance();
    try {
      await w.loadModelFromUrl(url, params);
    } catch (e) {
      try { await w.exit(); } catch (ignored) { /* 何もしない */ }
      throw e;
    }
    wllama = w;
    loadedGpu = gpu;
    let info = '';
    try {
      const c = w.getLoadedContextInfo();
      info = ' ctx=' + c.n_ctx + ' bos=' + c.add_bos_token + ' chat=' + c.has_chat;
    } catch (e) { /* 情報が無くても続ける */ }
    note('読み込み完了 ' + (gpu ? 'GPU' : 'CPU') + ' threads=' + (w.useMultiThread ? w.nbThreads : 1)
      + ' isolated=' + (self.crossOriginIsolated === true) + info);
  }

  function clean(text) {
    return String(text || '').replace(/<\|plamo:/g, '<| plamo:').replace(/\s+/g, ' ').trim();
  }

  function pickText(res) {
    if (!res) return '';
    const c = res.choices && res.choices[0];
    let t = '';
    if (c) t = (c.message && c.message.content) || c.text || '';
    if (!t && typeof res.content === 'string') t = res.content;
    return String(t).split('<|plamo:')[0].trim();
  }

  /** いま読み込んでいるモデルで 1 回推論する */
  async function infer(src, chat, maxTokens) {
    const ctl = new AbortController();
    const timer = setTimeout(() => ctl.abort(), TIMEOUT_MS);
    try {
      const common = { max_tokens: maxTokens || 256, temperature: 0, stop: STOP, abortSignal: ctl.signal };
      let res;
      if (chat) {
        res = await wllama.createChatCompletion(Object.assign({
          messages: [{ role: 'user', content: src }],
          chat_template_kwargs: { target_lang: 'ja' },
        }, common));
      } else {
        // PLaMo 3 Translate の PFML3 形式（先頭の BOS はトークナイザが付ける）
        const prompt = '<|plamo:tag|>dataset<|plamo:msg|>translation'
          + '<|plamo:tag|>input<|plamo:msg|>' + src
          + '<|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>ja<|plamo:msg|>';
        res = await wllama.createCompletion(Object.assign({ prompt }, common));
      }
      return pickText(res);
    } catch (e) {
      if (ctl.signal.aborted) throw new Error('時間切れ（' + (TIMEOUT_MS / 1000) + '秒）');
      throw e;
    } finally {
      clearTimeout(timer);
    }
  }

  /** 短い文を訳してみて、日本語が返る組み合わせを探す */
  async function selfTest() {
    const saved = readMode();
    const order = saved ? [saved].concat(MODES.filter(m => modeName(m) !== modeName(saved))) : MODES;
    for (const m of order) {
      if (m.gpu && !(navigator && navigator.gpu)) continue;
      const t0 = Date.now();
      try {
        await load(m.gpu);
        const out = await infer('Hello. This is a test of the translation model.', m.chat, 48);
        const sec = ((Date.now() - t0) / 1000).toFixed(1);
        if (hasJapanese(out)) {
          note('動作確認 OK ' + modeName(m) + ' ' + sec + '秒 「' + out.slice(0, 40) + '」');
          mode = m;
          writeMode(m);
          return;
        }
        note('動作確認 NG ' + modeName(m) + ' ' + sec + '秒 出力=「' + out.slice(0, 60) + '」');
      } catch (e) {
        note('動作確認 エラー ' + modeName(m) + ': ' + errText(e));
        await unload();
      }
    }
    await unload();
    throw new Error('この端末ではモデルから日本語の訳を得られませんでした');
  }

  /**
   * 使える状態にする。モデルを取得・読み込みし、短い文で動作確認する。
   */
  function ensureReady() {
    if (mode && wllama && wllama.isModelLoaded()) return Promise.resolve();
    if (loading) return loading;
    loading = selfTest();
    loading.catch(() => {}).finally(() => { loading = null; });
    return loading;
  }

  /** 1 件を日本語に訳す。同時に走らないよう順番待ちにする。 */
  function translate(text, maxTokens) {
    const src = clean(text);
    if (!src) return Promise.resolve('');
    const job = queue.then(async () => {
      await ensureReady();
      const t0 = Date.now();
      let out;
      try {
        out = await infer(src, mode.chat, maxTokens);
      } catch (e) {
        note('翻訳エラー ' + modeName(mode) + ': ' + errText(e));
        throw e;
      }
      if (!out) {
        note('翻訳結果が空 ' + modeName(mode) + ' 原文=「' + src.slice(0, 40) + '」');
        throw new Error('翻訳結果が空でした');
      }
      note('翻訳 ' + ((Date.now() - t0) / 1000).toFixed(1) + '秒 ' + src.length + '文字');
      return out;
    });
    queue = job.catch(() => {});
    return job;
  }

  function cached(id) {
    try { return JSON.parse(localStorage.getItem(CACHE_PREFIX + id) || 'null'); } catch (e) { return null; }
  }
  function store(id, data) {
    try { localStorage.setItem(CACHE_PREFIX + id, JSON.stringify(data)); } catch (e) { /* 容量不足などは無視 */ }
  }

  async function deleteModel() {
    await unload();
    mode = null;
    try { localStorage.removeItem(MODE_KEY); } catch (e) { /* 続ける */ }
    const mod = await import('/vendor/wllama/index.js');
    const cm = new mod.CacheManager();
    await cm.deleteMany(e => /plamo-3-translate/i.test(e.name));
  }

  async function isDownloaded() {
    try {
      const mod = await import('/vendor/wllama/index.js');
      const list = await new mod.CacheManager().list();
      return list.some(e => /plamo-3-translate/i.test(e.name) && e.size > 100 * 1024 * 1024);
    } catch (e) { return false; }
  }

  window.LocalTranslator = {
    MODEL_SIZE_LABEL,
    supported,
    isEnabled: () => readPref() && supported(),
    setEnabled: writePref,
    isReady: () => !!(mode && wllama && wllama.isModelLoaded()),
    diagnostics: () => diag.join('\n'),
    ensureReady,
    isDownloaded,
    deleteModel,
    async translateArticle(id, title, summary, withSummary) {
      const prev = cached(id) || {};
      const out = { titleJa: prev.titleJa || '', summaryJa: prev.summaryJa || '' };
      if (!out.titleJa) out.titleJa = await translate(title, 160);
      if (withSummary && summary && !out.summaryJa) out.summaryJa = await translate(String(summary).slice(0, 800), 512);
      store(id, out);
      return out;
    },
    cached,
  };
})();
