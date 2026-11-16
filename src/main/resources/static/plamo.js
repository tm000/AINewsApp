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

  /**
   * モデルを読み込む。未ダウンロードなら取得する。
   * onProgress(loaded, total) はダウンロード中に呼ばれる。
   */
  function ensureReady(onProgress) {
    if (wllama && wllama.isModelLoaded()) return Promise.resolve();
    if (loading) return loading;
    loading = (async () => {
      const progressCallback = ({ loaded, total }) => {
        if (onProgress) onProgress(loaded, total);
        const api = window.LocalTranslator;
        if (api && typeof api.onProgress === 'function') api.onProgress(loaded, total);
      };
      const base = { n_ctx: 2048, n_batch: 512, progressCallback };
      const url = await modelUrl();
      wllama = await createInstance();
      try {
        await wllama.loadModelFromUrl(url, base);
      } catch (e) {
        // WebGPU で失敗する端末もあるので CPU だけで再挑戦する
        console.warn('PLaMo: GPU での読み込みに失敗、CPU で再試行します', e);
        try { await wllama.exit(); } catch (ignored) { /* 何もしない */ }
        wllama = await createInstance();
        await wllama.loadModelFromUrl(url, Object.assign({}, base, { n_gpu_layers: 0 }));
      }
    })();
    loading.catch(() => { wllama = null; }).finally(() => { loading = null; });
    return loading;
  }

  function clean(text) {
    return String(text || '').replace(/<\|plamo:/g, '<| plamo:').replace(/\s+/g, ' ').trim();
  }

  /** 1 件を日本語に訳す。同時に走らないよう順番待ちにする。 */
  function translate(text, maxTokens) {
    const src = clean(text);
    if (!src) return Promise.resolve('');
    const job = queue.then(async () => {
      await ensureReady();
      // PLaMo 3 Translate の PFML3 形式（先頭の BOS はトークナイザが付ける）
      const prompt = '<|plamo:tag|>dataset<|plamo:msg|>translation'
        + '<|plamo:tag|>input<|plamo:msg|>' + src
        + '<|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>ja<|plamo:msg|>';
      const res = await wllama.createCompletion({
        prompt,
        max_tokens: maxTokens || 256,
        temperature: 0,
        stop: STOP,
      });
      return (res.choices[0].text || '').trim();
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
    try {
      if (wllama) { await wllama.exit(); }
    } catch (e) { /* 何もしない */ }
    wllama = null;
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
    isReady: () => !!(wllama && wllama.isModelLoaded()),
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
