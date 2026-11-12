// سرور هوش مصنوعی هوشیار — Cloudflare Worker + D1
// کلیدها فقط اینجا (Secrets) هستند و داخل برنامه‌ی اندروید نیستند.
// راه‌اندازی: README-SERVER.md

const J = { "content-type": "application/json; charset=utf-8" };
const out = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: J });
const err = (msg, status) => out({ error: { message: msg } }, status);
const num = (v, d) => (v === undefined || v === "" || isNaN(+v) ? d : +v);

// ───────── سهمیه‌ها (با متغیرهای محیطی قابل تغییرند) ─────────
function limits(env, paid) {
  return paid
    ? {
        chat: num(env.PAID_CHAT, 500),         // پیام در روز
        stt: num(env.PAID_STT, 150),           // تبدیل گفتار به متن در روز
        tts: num(env.PAID_TTS, 300),           // خواندن متن در روز
        image: num(env.PAID_IMAGE, 60),        // تصویر در روز
        elchars: num(env.PAID_ELEVEN_CHARS, 60000), // حروف ElevenLabs در ماه
        music: num(env.PAID_MUSIC, 0),         // آهنگ حرفه‌ای در ماه
        avatar: num(env.PAID_AVATAR, 20),      // کارتونی‌سازی با هوش مصنوعی در ماه
        enhance: num(env.PAID_ENHANCE, 40),    // بهبود کیفیت عکس با هوش مصنوعی در ماه
      }
    : {
        chat: num(env.FREE_CHAT, 30),
        stt: num(env.FREE_STT, 10),
        tts: num(env.FREE_TTS, 15),
        image: num(env.FREE_IMAGE, 6),
        elchars: 0,
        music: 0,
        avatar: 0,
        enhance: 0,
      };
}

// ───────── پایگاه داده ─────────
let ready = false;
async function init(env) {
  if (ready) return;
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS usage(dev TEXT, per TEXT, kind TEXT, n INTEGER DEFAULT 0, PRIMARY KEY(dev,per,kind))"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS sub(dev TEXT PRIMARY KEY, until INTEGER, plan TEXT)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS codes(code TEXT PRIMARY KEY, days INTEGER, used_by TEXT, used_at INTEGER, made_at INTEGER)"),
  ]);
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS refc(code TEXT PRIMARY KEY, dev TEXT UNIQUE)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS refs(invitee TEXT PRIMARY KEY, inviter TEXT, at INTEGER, rewarded INTEGER DEFAULT 0)"),
  ]);
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS accounts(email TEXT PRIMARY KEY, created INTEGER, last_login INTEGER, until INTEGER DEFAULT 0, note TEXT)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS acc_dev(dev TEXT PRIMARY KEY, email TEXT, at INTEGER)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS otp(email TEXT PRIMARY KEY, h TEXT, exp INTEGER, tries INTEGER DEFAULT 0)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS devs(dev TEXT PRIMARY KEY, first_seen INTEGER, last_seen INTEGER)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS banned(k TEXT PRIMARY KEY, why TEXT, at INTEGER)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS paid_tokens(token TEXT PRIMARY KEY, dev TEXT, product TEXT, at INTEGER)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS tglogin(token TEXT PRIMARY KEY, dev TEXT, exp INTEGER, tg_id TEXT, tg_name TEXT)"),
  ]);
  for (const col of ["price INTEGER", "note TEXT"]) {
    try { await env.DB.prepare("ALTER TABLE codes ADD COLUMN " + col).run(); } catch (e) { /* ستون از قبل هست */ }
  }
  ready = true;
}
const dayKey = () => "D" + new Date(Date.now() + 12600000).toISOString().slice(0, 10);
const monKey = () => "M" + new Date(Date.now() + 12600000).toISOString().slice(0, 7);

async function getN(env, dev, per, kind) {
  const r = await env.DB.prepare("SELECT n FROM usage WHERE dev=?1 AND per=?2 AND kind=?3").bind(dev, per, kind).first();
  return r ? r.n : 0;
}
async function addN(env, dev, per, kind, k) {
  await env.DB.prepare(
    "INSERT INTO usage(dev,per,kind,n) VALUES(?1,?2,?3,?4) ON CONFLICT(dev,per,kind) DO UPDATE SET n=MAX(0,n+?4)"
  ).bind(dev, per, kind, k).run();
}
async function subOf(env, dev) {
  const r = await env.DB.prepare("SELECT until FROM sub WHERE dev=?1").bind(dev).first();
  const a = await env.DB.prepare("SELECT a.until u FROM accounts a JOIN acc_dev d ON d.email=a.email WHERE d.dev=?1").bind(dev).first();
  const u = Math.max(r ? r.until : 0, a ? a.u || 0 : 0);
  return u > Date.now() ? u : 0;
}

// ───────── ورود ─────────
function deviceOf(req) {
  const m = /^Bearer\s+([A-Za-z0-9-]{16,64})$/.exec(req.headers.get("authorization") || "");
  return m ? m[1] : null;
}

async function guard(env, req, dev, kind, cost = 1) {
  // سقف کلی هر IP در روز، برای جلوگیری از ساختن شناسه‌ی جعلی
  const ip = req.headers.get("cf-connecting-ip") || "x";
  const ipCap = num(env.IP_DAILY, 400);
  const ipUsed = await getN(env, "ip:" + ip, dayKey(), "all");
  if (ipUsed >= ipCap) return err("تعداد درخواست‌های این شبکه امروز زیاد بود. فردا دوباره امتحان کن.", 429);
  const until = await subOf(env, dev);
  const L = limits(env, !!until);
  const used = await getN(env, dev, dayKey(), kind);
  if (used + 1 > L[kind]) {
    return err(
      until
        ? "سهمیه‌ی امروز اشتراک تمام شد. فردا دوباره تازه می‌شود."
        : "سهمیه‌ی رایگان امروز تمام شد. فردا دوباره امتحان کن یا اشتراک تهیه کن.",
      429
    );
  }
  await addN(env, dev, dayKey(), kind, 1);
  await addN(env, "ip:" + ip, dayKey(), "all", 1);
  return { until, L };
}

// ───────── چت (سازگار با OpenAI) ─────────
const CHAIN = [
  { name: "groq", base: "https://api.groq.com/openai/v1", env: "GROQ_KEY", model: "llama-3.3-70b-versatile", vision: "meta-llama/llama-4-scout-17b-16e-instruct", maxImg: 5 },
  { name: "cerebras", base: "https://api.cerebras.ai/v1", env: "CEREBRAS_KEY", model: "gpt-oss-120b" },
  { name: "mistral", base: "https://api.mistral.ai/v1", env: "MISTRAL_KEY", model: "mistral-small-latest", vision: "mistral-small-latest", maxImg: 8 },
  { name: "gemini", base: "https://generativelanguage.googleapis.com/v1beta/openai", env: "GEMINI_KEY", model: "gemini-2.5-flash", vision: "gemini-2.5-flash" },
  { name: "openrouter", base: "https://openrouter.ai/api/v1", env: "OPENROUTER_KEY", model: "openai/gpt-oss-20b:free" },
];
const GROQ_OK = new Set([
  "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-20b", "openai/gpt-oss-120b",
  "meta-llama/llama-4-scout-17b-16e-instruct",
]);
// کلیدی که سهمیه‌اش تمام شده (یا خراب است) مدتی کنار گذاشته می‌شود تا درخواست‌ها معطل آن نشوند
const cool = new Map();
const isCool = (k) => (cool.get(k) || 0) > Date.now();
const coolDown = (k, status) => {
  if (status === 429 || status === 402 || status === 503) cool.set(k, Date.now() + 10 * 60 * 1000);
  else if (status === 401 || status === 403) cool.set(k, Date.now() + 60 * 60 * 1000);
};
const shuffle = (a) => a.map((x) => [Math.random(), x]).sort((p, q) => p[0] - q[0]).map((p) => p[1]);

// فهرست مدل‌های هر سرویس (نیم‌ساعت در حافظه) تا اگر مدلی بازنشسته شد، خودکار مدل زنده انتخاب شود
const modelCache = new Map();
async function listModels(p, k) {
  const c = modelCache.get(p.name);
  if (c && Date.now() - c.at < 30 * 60 * 1000) return c.ids;
  try {
    const r = await fetch(p.base + "/models", { headers: { authorization: "Bearer " + k } });
    if (!r.ok) return c ? c.ids : [];
    const j = await r.json();
    const ids = (j.data || []).map((x) => String(x.id || "").replace(/^models\//, "")).filter(Boolean);
    if (ids.length) modelCache.set(p.name, { at: Date.now(), ids });
    return ids;
  } catch (e) { return c ? c.ids : []; }
}
const PREFER = {
  groq: ["llama-3.3-70b-versatile", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.1-8b-instant"],
  cerebras: ["gpt-oss-120b", "llama-3.3-70b", "llama3.1-8b"],
  mistral: ["mistral-small-latest", "mistral-medium-latest", "open-mistral-nemo"],
  gemini: ["gemini-2.5-flash", "gemini-flash-latest", "gemini-2.0-flash", "gemini-2.5-flash-lite"],
  openrouter: ["openai/gpt-oss-20b:free", "meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free"],
};
const BAD = /whisper|guard|tts|embed|moderation|orpheus|playai|speech|transcri|rerank|ocr|safeguard|image|audio|veo|imagen|lyria|live|robotics|computer-use/i;
async function pickModel(env, p, k, hasImage, wanted) {
  const override = env[p.name.toUpperCase() + "_MODEL"];
  if (override) return override;
  const ids = await listModels(p, k);
  if (!ids.length) return hasImage && p.vision ? p.vision : p.model;
  if (hasImage) {
    if (p.vision && ids.includes(p.vision)) return p.vision;
    const v = ids.find((id) => /scout|maverick|vision|llama-4|gemini|pixtral|mistral-(small|medium|large)/i.test(id) && !BAD.test(id));
    return v || p.vision || p.model;
  }
  if (wanted && p.name === "groq" && ids.includes(wanted) && !BAD.test(wanted)) return wanted;
  for (const m of PREFER[p.name] || []) if (ids.includes(m)) return m;
  const alt = ids.find((id) => !BAD.test(id) && (p.name !== "openrouter" || id.endsWith(":free")));
  return alt || p.model;
}

async function chat(req, env, until) {
  let body;
  try { body = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const msgsStr = JSON.stringify(body.messages || []);
  const hasImage = msgsStr.includes('"image_url"');
  const nImg = hasImage ? msgsStr.split('"image_url"').length - 1 : 0;
  const cap = until ? 6000 : 3000;
  if (!body.max_tokens || body.max_tokens > cap) body.max_tokens = cap;
  const attempts = [];
  for (const p of CHAIN) {
    if (hasImage && !p.vision) continue;
    if (nImg > (p.maxImg || 99)) continue; // چند عکس: سرویس‌هایی که سقف تعداد عکس دارند رد می‌شوند
    const keys = String(env[p.env] || "").split(",").map((s) => s.trim()).filter(Boolean);
    for (const k of shuffle(keys)) if (!isCool(k)) attempts.push({ p, k });
  }
  if (!attempts.length) return err("سرور هنوز کلید هوش مصنوعی ندارد (مدیر باید GROQ_KEY را بگذارد).", 503);
  let last = null;
  const log = [];
  for (const { p, k } of attempts.slice(0, 8)) {
    const b = { ...body };
    const want = b.model;
    b.model = await pickModel(env, p, k, hasImage, want);
    if (!(p.name === "groq" && b.model === want)) delete b.reasoning_effort;
    let r;
    try {
      r = await fetch(p.base + "/chat/completions", {
        method: "POST",
        headers: { "content-type": "application/json", authorization: "Bearer " + k },
        body: JSON.stringify(b),
      });
    } catch (e) { log.push(p.name + ":شبکه"); continue; }
    if (r.ok) return new Response(r.body, { status: 200, headers: { "content-type": r.headers.get("content-type") || "application/json" } });
    last = r;
    let why = "";
    try {
      const t = await r.clone().text();
      try { const o = JSON.parse(t); why = (o.error && (o.error.message || o.error.code)) || (Array.isArray(o) && o[0] && o[0].error && o[0].error.message) || ""; } catch { why = t; }
    } catch (e) { /* بدون جزئیات */ }
    why = String(why).replace(/\s+/g, " ").slice(0, 70);
    log.push(p.name + ":" + r.status + (why ? " [" + why + "]" : ""));
    coolDown(k, r.status);
    if (r.status === 400) break; // خطای خود درخواست، تکرارش فایده ندارد
  }
  if (last && last.status === 400) {
    return new Response(await last.text(), { status: 400, headers: J });
  }
  if (log.length) return err("سرویس‌های هوش مصنوعی جواب ندادند (" + log.join("، ") + "). چند دقیقه بعد دوباره امتحان کن.", 503);
  return err(attempts.length ? "هیچ سرویس هوش مصنوعی جواب نداد. چند لحظه بعد دوباره امتحان کن." : "سهمیه‌ی همه‌ی سرویس‌ها فعلاً پر است. چند دقیقه بعد دوباره امتحان کن.", 503);
}

async function stt(req, env) {
  const keys = String(env.GROQ_KEY || "").split(",").map((s) => s.trim()).filter(Boolean);
  if (!keys.length) return err("سرور کلید Groq ندارد.", 503);
  const ct = req.headers.get("content-type") || "";
  const buf = await req.arrayBuffer();
  for (const k of shuffle(keys).filter((x) => !isCool(x)).slice(0, 4)) {
    const r = await fetch("https://api.groq.com/openai/v1/audio/transcriptions", {
      method: "POST", headers: { "content-type": ct, authorization: "Bearer " + k }, body: buf,
    });
    if (r.ok || r.status === 400) return new Response(r.body, { status: r.status, headers: J });
    coolDown(k, r.status);
  }
  return err("تشخیص گفتار جواب نداد.", 503);
}

// ───────── صدا ─────────
const EDGE_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
const EDGE_VER = "143.0.3650.75";
const EDGE_VOICES = {
  fa: ["fa-IR-DilaraNeural", "fa-IR-FaridNeural"], en: ["en-US-JennyNeural", "en-US-GuyNeural"],
  ar: ["ar-SA-ZariyahNeural", "ar-SA-HamedNeural"], tr: ["tr-TR-EmelNeural", "tr-TR-AhmetNeural"],
  de: ["de-DE-KatjaNeural", "de-DE-ConradNeural"], fr: ["fr-FR-DeniseNeural", "fr-FR-HenriNeural"],
  es: ["es-ES-ElviraNeural", "es-ES-AlvaroNeural"], it: ["it-IT-ElsaNeural", "it-IT-DiegoNeural"],
  ru: ["ru-RU-SvetlanaNeural", "ru-RU-DmitryNeural"], zh: ["zh-CN-XiaoxiaoNeural", "zh-CN-YunxiNeural"],
  ja: ["ja-JP-NanamiNeural", "ja-JP-KeitaNeural"], ko: ["ko-KR-SunHiNeural", "ko-KR-InJoonNeural"],
};
const hex = (buf) => [...new Uint8Array(buf)].map((x) => x.toString(16).padStart(2, "0")).join("").toUpperCase();
const rid = () => crypto.randomUUID().replace(/-/g, "");
const xml = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");

async function edgeTts(text, lang, male) {
  const v = (EDGE_VOICES[lang] || EDGE_VOICES.en)[male ? 1 : 0];
  const secs = BigInt(Math.floor(Date.now() / 1000) + 11644473600);
  const ticks = (secs - (secs % 300n)) * 10000000n;
  const gec = hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(ticks.toString() + EDGE_TOKEN)));
  const url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
    `?TrustedClientToken=${EDGE_TOKEN}&ConnectionId=${rid()}&Sec-MS-GEC=${gec}&Sec-MS-GEC-Version=1-${EDGE_VER}`;
  const major = EDGE_VER.split(".")[0];
  const resp = await fetch(url, {
    headers: {
      Upgrade: "websocket", Pragma: "no-cache", "Cache-Control": "no-cache",
      Origin: "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
      "User-Agent": `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${major}.0.0.0 Safari/537.36 Edg/${major}.0.0.0`,
      Cookie: "muid=" + rid().toUpperCase() + ";",
    },
  });
  const ws = resp.webSocket;
  if (!ws) throw new Error("Edge " + resp.status);
  ws.accept();
  return await new Promise((resolve, reject) => {
    const chunks = [];
    const timer = setTimeout(() => { try { ws.close(); } catch {} reject(new Error("Edge timeout")); }, 20000);
    const finish = () => {
      clearTimeout(timer);
      const size = chunks.reduce((a, c) => a + c.length, 0);
      if (size < 100) return reject(new Error("Edge empty"));
      const o = new Uint8Array(size); let off = 0;
      for (const c of chunks) { o.set(c, off); off += c.length; }
      resolve(o);
    };
    ws.addEventListener("message", (ev) => {
      const d = ev.data;
      if (typeof d === "string") {
        if (d.includes("Path:turn.end")) { try { ws.close(); } catch {} finish(); }
      } else {
        const u = new Uint8Array(d);
        if (u.length < 2) return;
        const hl = (u[0] << 8) | u[1];
        const head = new TextDecoder().decode(u.slice(2, 2 + hl));
        if (head.includes("Path:audio")) chunks.push(u.slice(2 + hl));
      }
    });
    ws.addEventListener("error", () => { clearTimeout(timer); reject(new Error("Edge ws error")); });
    const ts = new Date().toString().replace(/ GMT.*/, "") + " GMT+0000 (Coordinated Universal Time)";
    ws.send(`X-Timestamp:${ts}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n` +
      `{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}\r\n`);
    ws.send(`X-RequestId:${rid()}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${ts}Z\r\nPath:ssml\r\n\r\n` +
      `<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><voice name='${v}'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>${xml(text)}</prosody></voice></speak>`);
  });
}

async function elevenTts(env, text, male) {
  const voice = male ? env.ELEVEN_VOICE_M || "JBFqnCBsd6RMkjVDRZzb" : env.ELEVEN_VOICE_F || "EXAVITQu4vr4xnSDxMaL";
  let lastErr = "ElevenLabs";
  for (const model of [env.ELEVEN_MODEL || "eleven_v3", "eleven_multilingual_v2"]) {
    const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voice}?output_format=mp3_44100_128`, {
      method: "POST",
      headers: { "xi-api-key": env.ELEVEN_KEY, "content-type": "application/json" },
      body: JSON.stringify({ text, model_id: model }),
    });
    if (r.ok) return new Uint8Array(await r.arrayBuffer());
    lastErr = "ElevenLabs " + r.status;
    if (r.status === 401 || r.status === 402) break;
  }
  throw new Error(lastErr);
}

// صدای طبیعی‌تر فارسی با Gemini TTS (اگر GEMINI_KEY هست و سهمیه دارد؛ وگرنه خودکار می‌رود سراغ Edge)
function wavOf(pcm, sr) {
  const h = new ArrayBuffer(44), v = new DataView(h);
  const s = (o, t) => { for (let i = 0; i < t.length; i++) v.setUint8(o + i, t.charCodeAt(i)); };
  s(0, "RIFF"); v.setUint32(4, 36 + pcm.length, true); s(8, "WAVE"); s(12, "fmt "); v.setUint32(16, 16, true);
  v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, sr, true); v.setUint32(28, sr * 2, true);
  v.setUint16(32, 2, true); v.setUint16(34, 16, true); s(36, "data"); v.setUint32(40, pcm.length, true);
  const o = new Uint8Array(44 + pcm.length); o.set(new Uint8Array(h), 0); o.set(pcm, 44); return o;
}
async function geminiTts(env, text, male) {
  const keys = shuffle(String(env.GEMINI_KEY || "").split(",").map((s) => s.trim()).filter(Boolean)).filter((k) => !isCool("gtts:" + k));
  if (!keys.length) throw new Error("Gemini TTS در استراحت");
  let last = "Gemini";
  for (const k of keys) {
    const r = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" + (env.GEMINI_TTS_MODEL || "gemini-2.5-flash-preview-tts") + ":generateContent", {
      method: "POST", headers: { "x-goog-api-key": k, "content-type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: "با لحن گرم، طبیعی و روان فارسی بخوان: " + text }] }],
        generationConfig: { responseModalities: ["AUDIO"], speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: male ? (env.GEMINI_VOICE_M || "Charon") : (env.GEMINI_VOICE_F || "Kore") } } } },
      }),
    });
    if (!r.ok) { coolDown("gtts:" + k, r.status); last = "Gemini " + r.status; continue; }
    const j = await r.json();
    const parts = (j.candidates && j.candidates[0] && j.candidates[0].content && j.candidates[0].content.parts) || [];
    const p = parts.find((x) => x.inlineData || x.inline_data);
    const d = p && (p.inlineData || p.inline_data).data;
    if (d) return wavOf(b64ToBytes(d), 24000);
    last = "Gemini خالی";
  }
  throw new Error(last);
}

async function tts(req, env, dev, until, L) {
  let b;
  try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const text = String(b.text || "").slice(0, until ? 1200 : 600).trim();
  if (!text) return err("متنی نیست.", 400);
  const male = ["onyx", "echo", "fable"].includes(b.voice);
  const lang = String(b.lang || "fa").slice(0, 2);
  const mon = monKey();
  const useEleven = !!(until && env.ELEVEN_KEY && L.elchars &&
    (lang !== "fa" || env.ELEVEN_FA === "1") &&
    (await getN(env, dev, mon, "elchars")) + text.length <= L.elchars);
  let src = "edge", audio, fails = [];
  if (useEleven) {
    try {
      audio = await elevenTts(env, text, male); src = "elevenlabs";
      await addN(env, dev, mon, "elchars", text.length);
    } catch (e) { fails.push(String(e.message || e)); }
  }
  if (!audio && lang === "fa" && env.GEMINI_KEY && env.GEMINI_TTS !== "0") {
    try { audio = await geminiTts(env, text, male); src = "gemini"; }
    catch (e) { fails.push(String(e.message || e)); }
  }
  if (!audio) {
    src = "edge";
    try { audio = await edgeTts(text, lang, male); }
    catch (e) { fails.push(String(e.message || e)); }
  }
  if (!audio) return err("ساخت صدا ممکن نشد (" + fails.join(" | ") + ")", 502);
  return new Response(audio, { headers: { "content-type": src === "gemini" ? "audio/wav" : "audio/mpeg", "x-voice-source": src } });
}

async function image(req, env, dev) {
  let b;
  try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const prompt = String(b.prompt || "").slice(0, 700).trim();
  if (!prompt) return err("توضیح تصویر خالی است.", 400);
  const clamp = (v, d) => Math.min(1536, Math.max(256, Math.round(+v || d)));
  const w = clamp(b.w, 1024), h = clamp(b.h, 1024), seed = Math.floor(+b.seed || Math.random() * 1e6);
  const q = encodeURIComponent(prompt);
  const urls = [];
  if (env.POLL_KEY) urls.push(`https://gen.pollinations.ai/image/${q}?width=${w}&height=${h}&seed=${seed}&model=flux&nologo=true&key=${env.POLL_KEY}`);
  urls.push(`https://image.pollinations.ai/prompt/${q}?width=${w}&height=${h}&seed=${seed}&nologo=true&model=flux`);
  for (let round = 0; round < 2; round++) {
    for (const u of urls) {
      try {
        const r = await fetch(u, { headers: { "user-agent": "Mozilla/5.0" } });
        const ct = r.headers.get("content-type") || "";
        if (r.ok && ct.startsWith("image/")) return new Response(r.body, { headers: { "content-type": ct } });
      } catch (e) { /* بعدی */ }
    }
  }
  await addN(env, dev, dayKey(), "image", -1);
  return err("ساخت تصویر جواب نداد. چند لحظه بعد دوباره امتحان کن.", 502);
}

async function music(req, env, dev, until, L) {
  if (!until) return err("ساخت آواز حرفه‌ای فقط برای اشتراک‌داران است. لطفاً اشتراک تهیه کنید.", 402);
  if (!env.ELEVEN_KEY) return err("سرور کلید ElevenLabs ندارد.", 503);
  const mon = monKey();
  if ((await getN(env, dev, mon, "music")) >= L.music) return err("سهمیه‌ی آواز حرفه‌ای این ماه تمام شد.", 429);
  await addN(env, dev, mon, "music", 1);
  const body = await req.text();
  const r = await fetch("https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128", {
    method: "POST", headers: { "xi-api-key": env.ELEVEN_KEY, "content-type": "application/json" }, body,
  });
  if (!r.ok) {
    await addN(env, dev, mon, "music", -1);
    return err("ساخت آواز ناموفق بود (ElevenLabs " + r.status + ").", 502);
  }
  return new Response(r.body, { headers: { "content-type": "audio/mpeg" } });
}

// ───────── تبدیل عکس به کارتون/آواتار (فقط اشتراک) ─────────
// اولویت: OPENAI_KEY (gpt-image-1، بهترین) ← GEMINI_KEY (gemini-2.5-flash-image، سهمیه‌ی رایگان دارد) ← در غیر این صورت binding «AI» کلودفلر (SD img2img، شباهت کمتر)
const AVATAR_STYLES = {
  flat: "Redraw this photo as a clean flat vector cartoon illustration, like a modern adult animated TV series: uniform dark outlines, flat colours with minimal cel shading, simplified details, plain light background. Keep the same person, full body, pose, face, beard, hairstyle and clothes exactly recognizable.",
  pixar: "Transform this photo into a Pixar-style 3D animated movie character. Keep the same person, face shape, hairstyle, skin tone and clothes so they are clearly recognizable. Soft cinematic lighting, big expressive eyes, clean smooth 3D render.",
  anime: "Redraw this photo as a high-quality anime illustration in a Japanese anime movie style. Keep the same person, hairstyle, hair colour, face features and clothes so they stay recognizable. Clean line art, soft cel shading, vibrant colours.",
  cartoon: "Turn this photo into a fun 2D cartoon character in a modern western cartoon style with bold outlines and flat colours. Keep the person's recognizable face, hairstyle and clothes.",
  figure: "Turn this person into a collectible 3D toy figure (vinyl art toy), glossy plastic, standing on a small base, studio lighting. Keep the face, hair and outfit recognizable.",
  caricature: "Make a friendly artistic caricature of this person with slightly exaggerated features, painted illustration style. Keep them clearly recognizable.",
  clay: "Turn this photo into a handmade claymation character (plasticine clay look, fingerprints texture, stop-motion style). Keep the person recognizable.",
  comic: "Redraw this photo as a comic-book superhero illustration with ink outlines, halftone shading and dramatic colours. Keep the person's face and hair recognizable.",
  watercolor: "Repaint this photo as an elegant watercolor portrait with soft washes and visible paper texture. Keep the person recognizable.",
};
const b64ToBytes = (s) => { const bin = atob(s); const u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u; };

async function avatar(req, env, dev, until, L) {
  if (!until) return err("این بخش فقط برای اشتراک‌داران است. لطفاً اشتراک تهیه کنید.", 402);
  if (!env.OPENAI_KEY && !env.GEMINI_KEY && !env.AI) return err("سرور هنوز برای کارتونی‌سازی تنظیم نشده است.", 503);
  let b;
  try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const img = String(b.image || "");
  if (img.length < 1000 || img.length > 4_000_000) return err("اندازه‌ی عکس مناسب نیست.", 400);
  const style = AVATAR_STYLES[b.style] ? b.style : "pixar";
  const mon = monKey();
  if ((await getN(env, dev, mon, "avatar")) >= L.avatar) return err("سهمیه‌ی کارتونی‌سازی این ماه تمام شد.", 429);
  await addN(env, dev, mon, "avatar", 1);
  const fail = async (m, s) => { await addN(env, dev, mon, "avatar", -1); return err(m, s); };
  try {
    const bytes = b64ToBytes(img);
    if (env.OPENAI_KEY) {
      const fd = new FormData();
      fd.append("model", env.AVATAR_MODEL || "gpt-image-1");
      fd.append("prompt", AVATAR_STYLES[style]);
      fd.append("size", "1024x1024");
      fd.append("quality", env.AVATAR_QUALITY || "medium");
      fd.append("input_fidelity", "high");
      fd.append("image", new Blob([bytes], { type: "image/jpeg" }), "photo.jpg");
      const r = await fetch("https://api.openai.com/v1/images/edits", { method: "POST", headers: { authorization: "Bearer " + env.OPENAI_KEY }, body: fd });
      if (!r.ok) return await fail("ساخت ناموفق بود (OpenAI " + r.status + ").", 502);
      const j = await r.json();
      const d = j.data && j.data[0] && j.data[0].b64_json;
      if (!d) return await fail("پاسخ خالی از سرویس تصویر.", 502);
      return new Response(b64ToBytes(d), { headers: { "content-type": "image/png" } });
    }
    if (env.GEMINI_KEY) {
      const keys = shuffle(String(env.GEMINI_KEY).split(",").map((s) => s.trim()).filter(Boolean));
      let last = 0;
      for (const k of keys) {
        const r = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" + (env.AVATAR_GEMINI_MODEL || "gemini-2.5-flash-image") + ":generateContent", {
          method: "POST", headers: { "x-goog-api-key": k, "content-type": "application/json" },
          body: JSON.stringify({ contents: [{ parts: [{ text: AVATAR_STYLES[style] }, { inline_data: { mime_type: "image/jpeg", data: img } }] }] }),
        });
        last = r.status;
        if (!r.ok) continue;
        const j = await r.json();
        const parts = (j.candidates && j.candidates[0] && j.candidates[0].content && j.candidates[0].content.parts) || [];
        const p = parts.find((x) => (x.inlineData || x.inline_data));
        const d = p && (p.inlineData || p.inline_data).data;
        if (d) return new Response(b64ToBytes(d), { headers: { "content-type": "image/png" } });
        last = 204;
      }
      if (!env.AI) return await fail("ساخت ناموفق بود (Gemini " + last + "). اگر سهمیه‌ی رایگان تمام شده بعداً امتحان کن.", 502);
    }
    const res = await env.AI.run("@cf/runwayml/stable-diffusion-v1-5-img2img", {
      prompt: AVATAR_STYLES[style].slice(0, 300), image: [...bytes], strength: 0.55, num_steps: 20, guidance: 8,
    });
    return new Response(res, { headers: { "content-type": "image/png" } });
  } catch (e) {
    return await fail("ساخت ناموفق بود: " + String(e && e.message ? e.message : e).slice(0, 80), 502);
  }
}

// ───────── بهبود کیفیت عکس با هوش مصنوعی (فقط اشتراک) ─────────
// اولویت: REPLICATE_TOKEN (Real-ESRGAN: وفادار به عکس، بزرگ‌نمایی ۲ برابر) ← GEMINI_KEY ← OPENAI_KEY
const ENH_PROMPT = "Restore and enhance this photo: make it sharper, remove noise and blur, recover fine details and natural colours, improve lighting. Keep exactly the same people, faces, composition and content; do not add, remove or change anything.";

async function enhanceAi(req, env, dev, until, L) {
  if (!until) return err("این بخش فقط برای اشتراک‌داران است. لطفاً اشتراک تهیه کنید.", 402);
  if (!env.REPLICATE_TOKEN && !env.GEMINI_KEY && !env.OPENAI_KEY) return err("سرور هنوز برای بهبود کیفیت با هوش مصنوعی تنظیم نشده است.", 503);
  let b;
  try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const img = String(b.image || "");
  if (img.length < 1000 || img.length > 4_000_000) return err("اندازه‌ی عکس مناسب نیست.", 400);
  const mon = monKey();
  if ((await getN(env, dev, mon, "enhance")) >= L.enhance) return err("سهمیه‌ی بهبود کیفیت این ماه تمام شد.", 429);
  await addN(env, dev, mon, "enhance", 1);
  const fail = async (m, s) => { await addN(env, dev, mon, "enhance", -1); return err(m, s); };
  const notes = [];
  try {
    if (env.REPLICATE_TOKEN) {
      try {
        const r = await fetch("https://api.replicate.com/v1/models/" + (env.ESRGAN_MODEL || "nightmareai/real-esrgan") + "/predictions", {
          method: "POST", headers: { authorization: "Bearer " + env.REPLICATE_TOKEN, "content-type": "application/json", prefer: "wait=60" },
          body: JSON.stringify({ input: { image: "data:image/jpeg;base64," + img, scale: 2, face_enhance: true } }),
        });
        let j = await r.json();
        if (r.ok || r.status === 201) {
          for (let i = 0; i < 25 && j && (j.status === "starting" || j.status === "processing"); i++) {
            await new Promise((ok) => setTimeout(ok, 2000));
            const g = await fetch(j.urls.get, { headers: { authorization: "Bearer " + env.REPLICATE_TOKEN } });
            j = await g.json();
          }
          const o = j && j.output ? (Array.isArray(j.output) ? j.output[0] : j.output) : null;
          if (j && j.status === "succeeded" && o) {
            const f = await fetch(o);
            if (f.ok) return new Response(f.body, { headers: { "content-type": f.headers.get("content-type") || "image/png" } });
          }
          notes.push("Replicate " + (j && j.status));
        } else notes.push("Replicate " + r.status);
      } catch (e) { notes.push("Replicate خطا"); }
    }
    if (env.GEMINI_KEY) {
      const keys = shuffle(String(env.GEMINI_KEY).split(",").map((s) => s.trim()).filter(Boolean));
      for (const k of keys) {
        const r = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" + (env.AVATAR_GEMINI_MODEL || "gemini-2.5-flash-image") + ":generateContent", {
          method: "POST", headers: { "x-goog-api-key": k, "content-type": "application/json" },
          body: JSON.stringify({ contents: [{ parts: [{ text: ENH_PROMPT }, { inline_data: { mime_type: "image/jpeg", data: img } }] }] }),
        });
        if (!r.ok) { notes.push("Gemini " + r.status); continue; }
        const j = await r.json();
        const parts = (j.candidates && j.candidates[0] && j.candidates[0].content && j.candidates[0].content.parts) || [];
        const p = parts.find((x) => x.inlineData || x.inline_data);
        const d = p && (p.inlineData || p.inline_data).data;
        if (d) return new Response(b64ToBytes(d), { headers: { "content-type": "image/png" } });
      }
    }
    if (env.OPENAI_KEY) {
      const fd = new FormData();
      fd.append("model", env.AVATAR_MODEL || "gpt-image-1"); fd.append("prompt", ENH_PROMPT); fd.append("size", "1024x1024");
      fd.append("quality", env.AVATAR_QUALITY || "medium"); fd.append("input_fidelity", "high");
      fd.append("image", new Blob([b64ToBytes(img)], { type: "image/jpeg" }), "photo.jpg");
      const r = await fetch("https://api.openai.com/v1/images/edits", { method: "POST", headers: { authorization: "Bearer " + env.OPENAI_KEY }, body: fd });
      if (r.ok) { const j = await r.json(); const d = j.data && j.data[0] && j.data[0].b64_json; if (d) return new Response(b64ToBytes(d), { headers: { "content-type": "image/png" } }); }
      else notes.push("OpenAI " + r.status);
    }
  } catch (e) { notes.push(String(e && e.message ? e.message : e).slice(0, 60)); }
  return await fail("بهبود کیفیت ناموفق بود (" + notes.join(" | ") + ").", 502);
}

// ───────── اشتراک با کد ─────────
async function redeem(req, env, dev) {
  let b;
  try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const code = String(b.code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  const bad = await getN(env, dev, dayKey(), "bad");
  if (bad >= 10) return err("تلاش ناموفق زیاد بود. فردا دوباره امتحان کن.", 429);
  const fmt = code.slice(0, 4) + "-" + code.slice(4, 8) + "-" + code.slice(8, 12);
  const row = await env.DB.prepare("SELECT days FROM codes WHERE code=?1 AND used_by IS NULL").bind(fmt).first();
  const res = row ? await env.DB.prepare("UPDATE codes SET used_by=?1, used_at=?2 WHERE code=?3 AND used_by IS NULL").bind(dev, Date.now(), fmt).run() : null;
  if (!row || !res || res.meta.changes !== 1) {
    await addN(env, dev, dayKey(), "bad", 1);
    return err("کد معتبر نیست یا قبلاً استفاده شده.", 400);
  }
  const until = await grant(env, dev, row.days);
  return out({ ok: true, until });
}
async function grant(env, dev, days) {
  const cur = await subOf(env, dev);
  const until = Math.max(Date.now(), cur) + days * 86400000;
  await env.DB.prepare("INSERT INTO sub(dev,until,plan) VALUES(?1,?2,'paid') ON CONFLICT(dev) DO UPDATE SET until=?2").bind(dev, until).run();
  await env.DB.prepare("UPDATE accounts SET until=MAX(COALESCE(until,0),?2) WHERE email=(SELECT email FROM acc_dev WHERE dev=?1)").bind(dev, until).run();
  return until;
}

// ───────── حساب کاربری (ورود با کد ایمیلی) ─────────
const sha = async (s) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map((x) => x.toString(16).padStart(2, "0")).join("");
const normEmail = (e) => String(e || "").trim().toLowerCase();
const okEmail = (e) => e.length <= 120 && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e);

async function tg(env, text) {
  if (!env.TG_BOT_TOKEN || !env.TG_CHAT_ID) return;
  try {
    await fetch("https://api.telegram.org/bot" + env.TG_BOT_TOKEN + "/sendMessage", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ chat_id: env.TG_CHAT_ID, text }),
    });
  } catch (e) { /* اطلاع‌رسانی اختیاری است */ }
}

async function sendMail(env, to, code) {
  if (!env.BREVO_KEY || !env.MAIL_FROM) return false;
  const r = await fetch("https://api.brevo.com/v3/smtp/email", {
    method: "POST", headers: { "api-key": env.BREVO_KEY, "content-type": "application/json" },
    body: JSON.stringify({
      sender: { email: env.MAIL_FROM, name: env.MAIL_NAME || "هوش مصنوعی هوشیار" },
      to: [{ email: to }],
      subject: "کد ورود هوشیار: " + code,
      htmlContent: '<div dir="rtl" style="font-family:Tahoma,sans-serif;font-size:15px"><p>سلام 👋</p><p>کد ورود شما به «هوش مصنوعی هوشیار»:</p><p style="font-size:30px;letter-spacing:6px;font-weight:bold;direction:ltr">' + code + '</p><p>این کد ۱۰ دقیقه اعتبار دارد. اگر شما درخواست نکرده‌اید، این ایمیل را نادیده بگیرید.</p></div>',
    }),
  });
  return r.ok;
}

async function authRequest(req, env, dev) {
  let b; try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const email = normEmail(b.email);
  if (!okEmail(email)) return err("ایمیل معتبر نیست.", 400);
  if (!env.BREVO_KEY) return err("ارسال ایمیل هنوز روی سرور تنظیم نشده است.", 503);
  const ip = req.headers.get("cf-connecting-ip") || "x";
  if ((await getN(env, "ip:" + ip, dayKey(), "otpreq")) >= 10) return err("درخواست زیاد بود. فردا دوباره امتحان کن.", 429);
  const last = await env.DB.prepare("SELECT exp FROM otp WHERE email=?1").bind(email).first();
  if (last && last.exp - 600000 > Date.now() - 45000) return err("کد را همین الان فرستادیم؛ حدود یک دقیقه صبر کن.", 429);
  await addN(env, "ip:" + ip, dayKey(), "otpreq", 1);
  const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
  await env.DB.prepare("INSERT INTO otp(email,h,exp,tries) VALUES(?1,?2,?3,0) ON CONFLICT(email) DO UPDATE SET h=?2, exp=?3, tries=0").bind(email, await sha(email + ":" + code), Date.now() + 600000).run();
  if (!(await sendMail(env, email, code))) return err("ارسال ایمیل ناموفق بود. چند دقیقه بعد دوباره امتحان کن.", 502);
  return out({ ok: true });
}

async function authVerify(req, env, dev) {
  let b; try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const email = normEmail(b.email), code = String(b.code || "").replace(/\D/g, "");
  const row = await env.DB.prepare("SELECT h,exp,tries FROM otp WHERE email=?1").bind(email).first();
  if (!row || row.exp < Date.now()) return err("کد منقضی شده؛ دوباره کد بگیر.", 400);
  if (row.tries >= 5) return err("تلاش اشتباه زیاد بود؛ کد جدید بگیر.", 429);
  if ((await sha(email + ":" + code)) !== row.h) {
    await env.DB.prepare("UPDATE otp SET tries=tries+1 WHERE email=?1").bind(email).run();
    return err("کد اشتباه است.", 400);
  }
  await env.DB.prepare("DELETE FROM otp WHERE email=?1").bind(email).run();
  const now = Date.now();
  const had = await env.DB.prepare("SELECT 1 x FROM accounts WHERE email=?1").bind(email).first();
  if (!had) await env.DB.prepare("INSERT INTO accounts(email,created,last_login,until) VALUES(?1,?2,?2,0)").bind(email, now).run();
  else await env.DB.prepare("UPDATE accounts SET last_login=?2 WHERE email=?1").bind(email, now).run();
  if (await env.DB.prepare("SELECT 1 x FROM banned WHERE k=?1").bind("e:" + email).first()) return err("این حساب مسدود شده است.", 403);
  const own = await env.DB.prepare("SELECT until FROM sub WHERE dev=?1").bind(dev).first();
  await env.DB.prepare("INSERT INTO acc_dev(dev,email,at) VALUES(?1,?2,?3) ON CONFLICT(dev) DO UPDATE SET email=?2, at=?3").bind(dev, email, now).run();
  await env.DB.prepare("UPDATE accounts SET until=MAX(COALESCE(until,0),?2) WHERE email=?1").bind(email, own ? own.until : 0).run();
  if (!had) await tg(env, "🆕 ثبت‌نام جدید در هوشیار\n" + email);
  return out({ ok: true, email, label: email, until: await subOf(env, dev) });
}

async function labelOf(env, email) {
  const a = await env.DB.prepare("SELECT note FROM accounts WHERE email=?1").bind(email).first();
  return (a && a.note) || email;
}
async function authMe(env, dev) {
  const r = await env.DB.prepare("SELECT email FROM acc_dev WHERE dev=?1").bind(dev).first();
  return out({ email: r ? r.email : null, label: r ? await labelOf(env, r.email) : null, until: await subOf(env, dev) });
}

// ───────── پرداخت درون‌برنامه‌ای کافه‌بازار ─────────
const PLAN_PRICE = { 1: 300, 2: 550, 3: 800, 4: 1000, 5: 1200, 6: 1400, 7: 1600, 8: 1800, 9: 2200, 10: 2400, 11: 2600, 12: 2800 };
const BZ = "https://pardakht.cafebazaar.ir/devapi/v2";
let bzTok = { t: "", exp: 0 };
async function bazaarToken(env, force) {
  if (!force && bzTok.t && bzTok.exp > Date.now()) return bzTok.t;
  const r = await fetch(BZ + "/auth/token/", {
    method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "refresh_token", client_id: env.BAZAAR_CLIENT_ID || "", client_secret: env.BAZAAR_CLIENT_SECRET || "", refresh_token: env.BAZAAR_REFRESH_TOKEN || "" }),
  });
  const j = await r.json().catch(() => ({}));
  if (!j.access_token) throw new Error("توکن بازار گرفته نشد (" + r.status + ")");
  bzTok = { t: j.access_token, exp: Date.now() + Math.max(60, (+j.expires_in || 3600) - 120) * 1000 };
  return bzTok.t;
}
async function bazaarValidate(env, product, token) {
  const pkg = env.BAZAAR_PACKAGE || "ir.hooshyar.app";
  for (let attempt = 0; attempt < 2; attempt++) {
    const at = await bazaarToken(env, attempt > 0);
    const r = await fetch(BZ + "/api/validate/" + encodeURIComponent(pkg) + "/inapp/" + encodeURIComponent(product) + "/purchases/" + encodeURIComponent(token) + "/?access_token=" + encodeURIComponent(at), { method: "POST" });
    if (r.status === 401 || r.status === 403) continue;
    const j = await r.json().catch(() => ({}));
    return { ok: r.ok, status: r.status, j };
  }
  return { ok: false, status: 401, j: {} };
}
async function payBazaar(req, env, dev) {
  let b; try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const product = String(b.productId || ""), token = String(b.purchaseToken || "");
  const m = /^plan_(\d{1,2})m$/.exec(product);
  const months = m ? +m[1] : 0;
  if (!PLAN_PRICE[months] || token.length < 8 || token.length > 400) return err("محصول یا توکن نامعتبر است.", 400);
  if (!env.BAZAAR_CLIENT_ID || !env.BAZAAR_REFRESH_TOKEN) return err("پرداخت بازار هنوز روی سرور تنظیم نشده است.", 503);
  const done = await env.DB.prepare("SELECT dev FROM paid_tokens WHERE token=?1").bind(token).first();
  if (done) return done.dev === dev ? out({ ok: true, already: true, until: await subOf(env, dev) }) : err("این خرید قبلاً برای حساب دیگری ثبت شده است.", 409);
  let v;
  try { v = await bazaarValidate(env, product, token); } catch (e) { return err("بررسی خرید ناموفق بود: " + String(e.message || e).slice(0, 80), 502); }
  if (!v.ok) return err("خرید در بازار تأیید نشد (" + v.status + ").", 402);
  if (v.j && v.j.purchaseState !== undefined && +v.j.purchaseState !== 0) return err("این خرید معتبر نیست (برگشت‌خورده یا لغوشده).", 402);
  const ins = await env.DB.prepare("INSERT OR IGNORE INTO paid_tokens(token,dev,product,at) VALUES(?1,?2,?3,?4)").bind(token, dev, product, Date.now()).run();
  if (ins.meta.changes !== 1) return err("این خرید قبلاً ثبت شده است.", 409);
  const days = months === 12 ? 365 : months * 30;
  const until = await grant(env, dev, days);
  await env.DB.prepare("INSERT INTO codes(code,days,used_by,used_at,made_at,price,note) VALUES(?1,?2,?3,?4,?4,?5,?6)").bind("BZ-" + newCode(), days, dev, Date.now(), PLAN_PRICE[months], "کافه‌بازار " + product).run();
  const em = await env.DB.prepare("SELECT email FROM acc_dev WHERE dev=?1").bind(dev).first();
  await tg(env, "💰 خرید جدید در بازار: " + months + " ماه (" + PLAN_PRICE[months] + " هزار تومان)\n" + (em ? await labelOf(env, em.email) : dev.slice(0, 12)));
  return out({ ok: true, until });
}

// ───────── پرداخت درون‌برنامه‌ای مایکت ─────────
async function payMyket(req, env, dev) {
  let b; try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const product = String(b.productId || ""), token = String(b.purchaseToken || "");
  const m = /^plan_(\d{1,2})m$/.exec(product);
  const months = m ? +m[1] : 0;
  if (!PLAN_PRICE[months] || token.length < 8 || token.length > 400) return err("محصول یا توکن نامعتبر است.", 400);
  if (!env.MYKET_TOKEN) return err("پرداخت مایکت هنوز روی سرور تنظیم نشده است.", 503);
  const done = await env.DB.prepare("SELECT dev FROM paid_tokens WHERE token=?1").bind(token).first();
  if (done) return done.dev === dev ? out({ ok: true, already: true, until: await subOf(env, dev) }) : err("این خرید قبلاً برای حساب دیگری ثبت شده است.", 409);
  const pkg = env.MYKET_PACKAGE || "ir.hooshyar.app";
  let r, j = {};
  try {
    r = await fetch("https://developer.myket.ir/api/partners/applications/" + encodeURIComponent(pkg) + "/purchases/products/" + encodeURIComponent(product) + "/tokens/" + encodeURIComponent(token), { headers: { "X-Access-Token": env.MYKET_TOKEN } });
    j = await r.json().catch(() => ({}));
  } catch (e) { return err("بررسی خرید ناموفق بود.", 502); }
  if (!r.ok) return err("خرید در مایکت تأیید نشد (" + r.status + ").", 402);
  if (j.purchaseState !== undefined && +j.purchaseState !== 0) return err("این خرید معتبر نیست.", 402);
  const ins = await env.DB.prepare("INSERT OR IGNORE INTO paid_tokens(token,dev,product,at) VALUES(?1,?2,?3,?4)").bind(token, dev, product, Date.now()).run();
  if (ins.meta.changes !== 1) return err("این خرید قبلاً ثبت شده است.", 409);
  const days = months === 12 ? 365 : months * 30;
  const until = await grant(env, dev, days);
  await env.DB.prepare("INSERT INTO codes(code,days,used_by,used_at,made_at,price,note) VALUES(?1,?2,?3,?4,?4,?5,?6)").bind("MK-" + newCode(), days, dev, Date.now(), PLAN_PRICE[months], "مایکت " + product).run();
  const em = await env.DB.prepare("SELECT email FROM acc_dev WHERE dev=?1").bind(dev).first();
  await tg(env, "💰 خرید جدید در مایکت: " + months + " ماه (" + PLAN_PRICE[months] + " هزار تومان)\n" + (em ? await labelOf(env, em.email) : dev.slice(0, 12)));
  return out({ ok: true, until });
}

// ───────── ورود با تلگرام ─────────
const tgApi = async (env, method, body) => {
  const r = await fetch("https://api.telegram.org/bot" + env.TG_BOT_TOKEN + "/" + method, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body || {}),
  });
  return r.json().catch(() => ({}));
};
let botName = "";
const tgSecret = async (env) => (await sha("tgwh:" + env.TG_BOT_TOKEN)).slice(0, 32);

async function tgStart(req, env, dev) {
  if (!env.TG_BOT_TOKEN) return err("ورود با تلگرام هنوز روی سرور تنظیم نشده است.", 503);
  const ip = req.headers.get("cf-connecting-ip") || "x";
  if ((await getN(env, "ip:" + ip, dayKey(), "tgstart")) >= 20) return err("درخواست زیاد بود. فردا دوباره امتحان کن.", 429);
  await addN(env, "ip:" + ip, dayKey(), "tgstart", 1);
  if (!botName) { const me = await tgApi(env, "getMe"); botName = me && me.result ? me.result.username : ""; }
  if (!botName) return err("ربات تلگرام در دسترس نیست.", 502);
  const token = [...crypto.getRandomValues(new Uint8Array(12))].map((x) => ALPHA[x % ALPHA.length]).join("");
  await env.DB.prepare("DELETE FROM tglogin WHERE dev=?1 OR exp<?2").bind(dev, Date.now()).run();
  await env.DB.prepare("INSERT INTO tglogin(token,dev,exp) VALUES(?1,?2,?3)").bind(token, dev, Date.now() + 600000).run();
  return out({ token, link: "https://t.me/" + botName + "?start=" + token, bot: botName });
}

async function tgCheck(req, env, dev) {
  let b; try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const row = await env.DB.prepare("SELECT tg_id, tg_name, exp FROM tglogin WHERE token=?1 AND dev=?2").bind(String(b.token || ""), dev).first();
  if (!row || row.exp < Date.now()) return err("درخواست ورود منقضی شده؛ دوباره امتحان کن.", 410);
  if (!row.tg_id) return out({ done: false });
  const email = "tg:" + row.tg_id, now = Date.now();
  await env.DB.prepare("DELETE FROM tglogin WHERE token=?1").bind(String(b.token)).run();
  if (await env.DB.prepare("SELECT 1 x FROM banned WHERE k=?1").bind("e:" + email).first()) return err("این حساب مسدود شده است.", 403);
  const had = await env.DB.prepare("SELECT 1 x FROM accounts WHERE email=?1").bind(email).first();
  if (!had) await env.DB.prepare("INSERT INTO accounts(email,created,last_login,until,note) VALUES(?1,?2,?2,0,?3)").bind(email, now, row.tg_name || "").run();
  else await env.DB.prepare("UPDATE accounts SET last_login=?2, note=COALESCE(NULLIF(?3,''),note) WHERE email=?1").bind(email, now, row.tg_name || "").run();
  const own = await env.DB.prepare("SELECT until FROM sub WHERE dev=?1").bind(dev).first();
  await env.DB.prepare("INSERT INTO acc_dev(dev,email,at) VALUES(?1,?2,?3) ON CONFLICT(dev) DO UPDATE SET email=?2, at=?3").bind(dev, email, now).run();
  await env.DB.prepare("UPDATE accounts SET until=MAX(COALESCE(until,0),?2) WHERE email=?1").bind(email, own ? own.until : 0).run();
  if (!had) await tg(env, "🆕 ثبت‌نام جدید در هوشیار (تلگرام)\n" + (row.tg_name || email));
  return out({ done: true, email, label: await labelOf(env, email), until: await subOf(env, dev) });
}

async function tgWebhook(req, env) {
  if (!env.TG_BOT_TOKEN || req.headers.get("x-telegram-bot-api-secret-token") !== (await tgSecret(env))) return new Response("no", { status: 403 });
  const u = await req.json().catch(() => ({}));
  const m = u.message;
  if (!m || !m.text || !m.from) return new Response("ok");
  const say = (text) => tgApi(env, "sendMessage", { chat_id: m.chat.id, text });
  const mm = /^\/start(?:\s+([A-Z0-9]{12}))?/.exec(m.text.trim());
  if (!mm) { await say("برای ورود، از داخل اپ «هوش مصنوعی هوشیار» دکمه‌ی «ورود با تلگرام» را بزن."); return new Response("ok"); }
  if (!mm[1]) { await say("سلام 👋 این ربات برای ورود به اپ «هوش مصنوعی هوشیار» است. دکمه‌ی «ورود با تلگرام» را داخل اپ بزن."); return new Response("ok"); }
  const row = await env.DB.prepare("SELECT exp FROM tglogin WHERE token=?1").bind(mm[1]).first();
  if (!row || row.exp < Date.now()) { await say("این درخواست ورود منقضی شده. دوباره از داخل اپ امتحان کن."); return new Response("ok"); }
  const f = m.from;
  const name = (f.username ? "@" + f.username : [f.first_name, f.last_name].filter(Boolean).join(" ")) || String(f.id);
  await env.DB.prepare("UPDATE tglogin SET tg_id=?2, tg_name=?3 WHERE token=?1").bind(mm[1], String(f.id), name.slice(0, 60)).run();
  await say("✅ وارد شدی! حالا به اپ هوشیار برگرد.");
  return new Response("ok");
}
async function authOut(env, dev) {
  await env.DB.prepare("DELETE FROM acc_dev WHERE dev=?1").bind(dev).run();
  return out({ ok: true });
}

// ───────── دعوت دوستان ─────────
// دعوت‌شونده بلافاصله چند روز اشتراک می‌گیرد؛ دعوت‌کننده وقتی جایزه می‌گیرد که دوستش واقعاً از اپ استفاده کرده باشد (حداقل ۵ درخواست).
async function refCodeOf(env, dev) {
  const row = await env.DB.prepare("SELECT code FROM refc WHERE dev=?1").bind(dev).first();
  if (row) return row.code;
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode("ref:" + dev)));
  for (let o = 0; o < 20; o++) {
    const code = [...h.slice(o, o + 6)].map((x) => ALPHA[x % ALPHA.length]).join("");
    try { await env.DB.prepare("INSERT INTO refc(code,dev) VALUES(?1,?2)").bind(code, dev).run(); return code; } catch (e) { /* تکراری */ }
    const again = await env.DB.prepare("SELECT code FROM refc WHERE dev=?1").bind(dev).first();
    if (again) return again.code;
  }
  return "";
}
const refCfg = (env) => ({ invitee: +env.REF_INVITEE_DAYS || 3, inviter: +env.REF_INVITER_DAYS || 7, max: +env.REF_MAX || 10 });

async function refInfo(env, dev) {
  const C = refCfg(env);
  const code = await refCodeOf(env, dev);
  let done = (await env.DB.prepare("SELECT COUNT(*) c FROM refs WHERE inviter=?1 AND rewarded=1").bind(dev).first()).c;
  const pend = (await env.DB.prepare("SELECT invitee FROM refs WHERE inviter=?1 AND rewarded=0").bind(dev).all()).results;
  for (const p of pend) {
    if (done >= C.max) break;
    const u = await env.DB.prepare("SELECT COALESCE(SUM(n),0) s FROM usage WHERE dev=?1 AND kind NOT IN ('bad','badadmin')").bind(p.invitee).first();
    if (u.s >= 5) {
      const r = await env.DB.prepare("UPDATE refs SET rewarded=1 WHERE invitee=?1 AND rewarded=0").bind(p.invitee).run();
      if (r.meta.changes === 1) { await grant(env, dev, C.inviter); done++; }
    }
  }
  const total = (await env.DB.prepare("SELECT COUNT(*) c FROM refs WHERE inviter=?1").bind(dev).first()).c;
  const used = await env.DB.prepare("SELECT 1 x FROM refs WHERE invitee=?1").bind(dev).first();
  return out({ code, invited: total, rewarded: done, earnedDays: done * C.inviter, max: C.max, inviterDays: C.inviter, inviteeDays: C.invitee, usedCode: !!used });
}

async function refUse(req, env, dev) {
  let b;
  try { b = await req.json(); } catch { return err("درخواست نامعتبر", 400); }
  const code = String(b.code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  const ip = req.headers.get("cf-connecting-ip") || "x";
  if ((await getN(env, "ip:" + ip, dayKey(), "refuse")) >= 3) return err("امروز بیش از حد تلاش شد. فردا دوباره امتحان کن.", 429);
  await addN(env, "ip:" + ip, dayKey(), "refuse", 1);
  const row = code.length === 6 ? await env.DB.prepare("SELECT dev FROM refc WHERE code=?1").bind(code).first() : null;
  if (!row) return err("کد دعوت معتبر نیست.", 400);
  if (row.dev === dev) return err("کد دعوت خودت را نمی‌توانی وارد کنی.", 400);
  if (await env.DB.prepare("SELECT 1 x FROM refs WHERE invitee=?1").bind(dev).first()) return err("قبلاً یک کد دعوت وارد کرده‌ای.", 400);
  await env.DB.prepare("INSERT INTO refs(invitee,inviter,at) VALUES(?1,?2,?3)").bind(dev, row.dev, Date.now()).run();
  const C = refCfg(env);
  const until = await grant(env, dev, C.invitee);
  return out({ ok: true, days: C.invitee, until });
}

async function me(env, dev) {
  const until = await subOf(env, dev);
  const L = limits(env, !!until);
  const d = dayKey(), m = monKey();
  return out({
    plan: until ? "paid" : "free", until,
    chat: [await getN(env, dev, d, "chat"), L.chat], stt: [await getN(env, dev, d, "stt"), L.stt],
    tts: [await getN(env, dev, d, "tts"), L.tts], image: [await getN(env, dev, d, "image"), L.image], music: [await getN(env, dev, m, "music"), L.music],
  });
}

// ───────── مدیریت ─────────
const ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function newCode() {
  const r = crypto.getRandomValues(new Uint8Array(12));
  const s = [...r].map((x) => ALPHA[x % ALPHA.length]).join("");
  return s.slice(0, 4) + "-" + s.slice(4, 8) + "-" + s.slice(8, 12);
}
async function admin(req, env, url) {
  const base = "/" + (env.ADMIN_PATH || "admin").replace(/^\/+|\/+$/g, "");
  if (url.pathname === base + "/manifest.json") {
    return new Response(JSON.stringify({
      name: "مدیریت هوشیار", short_name: "مدیریت", lang: "fa", dir: "rtl", display: "standalone",
      start_url: base + "/", scope: base + "/", background_color: "#0f1020", theme_color: "#0f1020",
      icons: [{ src: base + "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }],
    }), { headers: { "content-type": "application/manifest+json" } });
  }
  if (url.pathname === base + "/icon.svg") {
    return new Response(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7c6cff"/><stop offset="1" stop-color="#3fa2f7"/></linearGradient></defs><rect width="512" height="512" rx="112" fill="url(#g)"/><path d="M256 96l128 48v96c0 88-56 142-128 176-72-34-128-88-128-176v-96z" fill="#fff"/><path d="M216 252l30 30 54-60" stroke="#6c5cff" stroke-width="28" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>`, { headers: { "content-type": "image/svg+xml" } });
  }
  if (url.pathname === base || url.pathname === base + "/") {
    return new Response(ADMIN_HTML.replaceAll("__BASE__", base), { headers: { "content-type": "text/html; charset=utf-8", "x-robots-tag": "noindex", "cache-control": "no-store" } });
  }
  if (url.pathname !== base + "/api" || req.method !== "POST") return err("مسیر ناشناخته", 404);
  // قفل تلاش‌های اشتباه رمز: ۸ بار در روز برای هر IP
  const ip = req.headers.get("cf-connecting-ip") || "x";
  if ((await getN(env, "ip:" + ip, dayKey(), "badadmin")) >= 8) return err("تلاش ناموفق زیاد بود. فردا دوباره امتحان کن.", 429);
  const given = req.headers.get("x-admin") || "";
  const ok = !!env.ADMIN_TOKEN && given.length === env.ADMIN_TOKEN.length &&
    [...given].reduce((d, ch, i) => d | (ch.charCodeAt(0) ^ env.ADMIN_TOKEN.charCodeAt(i)), 0) === 0;
  if (!ok) {
    await addN(env, "ip:" + ip, dayKey(), "badadmin", 1);
    return err("اجازه نداری", 401);
  }
  const b = await req.json().catch(() => ({}));
  if (b.action === "codes") {
    const n = Math.min(Math.max(+b.count || 1, 1), 100), days = Math.min(Math.max(+b.days || 30, 1), 3650);
    const list = [];
    for (let i = 0; i < n; i++) {
      const c = newCode();
      await env.DB.prepare("INSERT INTO codes(code,days,made_at,price,note) VALUES(?1,?2,?3,?4,?5)").bind(c, days, Date.now(), Math.max(0, +b.price || 0), String(b.note || "").slice(0, 80)).run();
      list.push(c);
    }
    return out({ codes: list, days });
  }
  if (b.action === "diag") {
    const res = [];
    for (const p of CHAIN) {
      const keys = String(env[p.env] || "").split(",").map((x) => x.trim()).filter(Boolean);
      if (!keys.length) { res.push({ name: p.name, status: "کلید ندارد" }); continue; }
      for (let i = 0; i < keys.length; i++) {
        try {
          const r = await fetch(p.base + "/models", { headers: { authorization: "Bearer " + keys[i] } });
          let model = "";
          if (r.ok && i === 0) { modelCache.delete(p.name); model = await pickModel(env, p, keys[i], false, ""); }
          res.push({ name: p.name, n: i + 1, status: r.status, ok: r.ok, model });
        } catch (e) { res.push({ name: p.name, n: i + 1, status: "خطای شبکه" }); }
      }
    }
    return out({ results: res });
  }
  if (b.action === "list") {
    const r = await env.DB.prepare("SELECT code,days,used_by,used_at,made_at,price,note FROM codes ORDER BY made_at DESC LIMIT 80").all();
    return out({ codes: r.results });
  }
  if (b.action === "revoke") {
    const c = String(b.code || "").toUpperCase();
    const r = await env.DB.prepare("DELETE FROM codes WHERE code=?1 AND used_by IS NULL").bind(c).run();
    return out({ ok: r.meta.changes === 1 });
  }
  if (b.action === "grant") {
    const dev = String(b.device || "").trim();
    if (!/^[A-Za-z0-9-]{16,64}$/.test(dev)) return err("شناسه‌ی دستگاه نامعتبر", 400);
    const days = Math.max(+b.days || 30, 1), price = Math.max(0, +b.price || 0);
    if (price > 0) {
      await env.DB.prepare("INSERT INTO codes(code,days,used_by,used_at,made_at,price,note) VALUES(?1,?2,?3,?4,?4,?5,?6)")
        .bind("MAN-" + newCode(), days, dev, Date.now(), price, String(b.note || "").slice(0, 80)).run();
    }
    return out({ until: await grant(env, dev, days) });
  }
  if (b.action === "bazaarExchange") {
    if (!env.BAZAAR_CLIENT_ID || !env.BAZAAR_CLIENT_SECRET) return err("اول BAZAAR_CLIENT_ID و BAZAAR_CLIENT_SECRET را در Cloudflare بگذار.", 400);
    const r = await fetch(BZ + "/auth/token/", {
      method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "authorization_code", code: String(b.code || "").trim(), client_id: env.BAZAAR_CLIENT_ID, client_secret: env.BAZAAR_CLIENT_SECRET, redirect_uri: String(b.redirect || url.origin + "/").trim() }),
    });
    const j = await r.json().catch(() => ({}));
    if (!j.refresh_token) return err("دریافت توکن ناموفق بود (" + r.status + "): " + JSON.stringify(j).slice(0, 160), 400);
    return out({ refresh_token: j.refresh_token });
  }
  if (b.action === "tgTest") {
    if (!env.TG_BOT_TOKEN) return err("TG_BOT_TOKEN در Cloudflare نیست.", 400);
    if (!env.TG_CHAT_ID) return err("TG_CHAT_ID در Cloudflare نیست.", 400);
    const r = await tgApi(env, "sendMessage", { chat_id: env.TG_CHAT_ID, text: "✅ پیام آزمایشی هوشیار: اطلاع‌رسانی خرید کار می‌کند." });
    return r.ok ? out({ ok: true }) : err("تلگرام نپذیرفت: " + String(r.description || "خطای نامشخص").slice(0, 120), 400);
  }
  if (b.action === "bazaarCheck") {
    try { await bazaarToken(env, true); return out({ ok: true }); } catch (e) { return err(String(e.message || e), 400); }
  }
  if (b.action === "tgSetup") {
    if (!env.TG_BOT_TOKEN) return err("متغیر TG_BOT_TOKEN تنظیم نشده است.", 400);
    const me = await tgApi(env, "getMe");
    if (!me.ok) return err("توکن ربات معتبر نیست.", 400);
    const r = await tgApi(env, "setWebhook", { url: url.origin + "/tg", secret_token: await tgSecret(env), allowed_updates: ["message"], drop_pending_updates: true });
    return out({ ok: !!r.ok, bot: me.result.username, desc: r.description || "" });
  }
  if (b.action === "users") {
    const q = String(b.q || "").trim().toLowerCase();
    const like = "%" + q + "%";
    const accs = (await env.DB.prepare("SELECT a.email, a.created, a.last_login, a.until, (SELECT COUNT(*) FROM acc_dev d WHERE d.email=a.email) devices FROM accounts a WHERE (?1='' OR a.email LIKE ?2 OR LOWER(COALESCE(a.note,'')) LIKE ?2) ORDER BY a.created DESC LIMIT 40").bind(q, like).all()).results;
    let devs = [];
    if (q.length >= 4) devs = (await env.DB.prepare("SELECT d.dev, d.first_seen AS first, d.last_seen AS last, (SELECT email FROM acc_dev x WHERE x.dev=d.dev) email, (SELECT until FROM sub s WHERE s.dev=d.dev) until FROM devs d WHERE d.dev LIKE ?1 ORDER BY d.last_seen DESC LIMIT 20").bind(like).all()).results;
    else if (!q) devs = (await env.DB.prepare("SELECT d.dev, d.first_seen AS first, d.last_seen AS last, (SELECT email FROM acc_dev x WHERE x.dev=d.dev) email, (SELECT until FROM sub s WHERE s.dev=d.dev) until FROM devs d ORDER BY d.last_seen DESC LIMIT 20").all()).results;
    return out({ accounts: accs, devices: devs });
  }
  if (b.action === "user") {
    const email = normEmail(b.email), dev = String(b.device || "").trim();
    let devList = [], acc = null;
    if (email) {
      acc = await env.DB.prepare("SELECT * FROM accounts WHERE email=?1").bind(email).first();
      devList = (await env.DB.prepare("SELECT dev FROM acc_dev WHERE email=?1").bind(email).all()).results.map((x) => x.dev);
    } else if (dev) devList = [dev];
    const info = [];
    for (const dv of devList) {
      const d = await env.DB.prepare("SELECT first_seen AS first, last_seen AS last FROM devs WHERE dev=?1").bind(dv).first();
      const s = await env.DB.prepare("SELECT until FROM sub WHERE dev=?1").bind(dv).first();
      const use = (await env.DB.prepare("SELECT kind, SUM(n) s FROM usage WHERE dev=?1 AND kind NOT IN ('bad') GROUP BY kind").bind(dv).all()).results;
      const today = (await env.DB.prepare("SELECT kind, n FROM usage WHERE dev=?1 AND per=?2").bind(dv, dayKey()).all()).results;
      const codes = (await env.DB.prepare("SELECT code,days,used_at,price,note FROM codes WHERE used_by=?1 ORDER BY used_at DESC").bind(dv).all()).results;
      const rc = await env.DB.prepare("SELECT code FROM refc WHERE dev=?1").bind(dv).first();
      const inv = (await env.DB.prepare("SELECT COUNT(*) c, COALESCE(SUM(rewarded),0) r FROM refs WHERE inviter=?1").bind(dv).first());
      const by = await env.DB.prepare("SELECT inviter FROM refs WHERE invitee=?1").bind(dv).first();
      const ban = await env.DB.prepare("SELECT why FROM banned WHERE k=?1").bind(dv).first();
      info.push({ dev: dv, first: d && d.first, last: d && d.last, until: s ? s.until : 0, total: use, today, codes, refCode: rc && rc.code, invited: inv.c, rewarded: inv.r, invitedBy: by && by.inviter, banned: ban ? ban.why || "مسدود" : null });
    }
    const ebn = email ? await env.DB.prepare("SELECT why FROM banned WHERE k=?1").bind("e:" + email).first() : null;
    return out({ account: acc, devices: info, bannedEmail: ebn ? ebn.why || "مسدود" : null });
  }
  if (b.action === "setSub") {
    const email = normEmail(b.email), dev = String(b.device || "").trim();
    const until = Math.max(0, +b.until || 0);
    if (email) {
      await env.DB.prepare("UPDATE accounts SET until=?2 WHERE email=?1").bind(email, until).run();
      for (const x of (await env.DB.prepare("SELECT dev FROM acc_dev WHERE email=?1").bind(email).all()).results) await env.DB.prepare("UPDATE sub SET until=?2 WHERE dev=?1").bind(x.dev, until).run();
    } else if (dev) await env.DB.prepare("UPDATE sub SET until=?2 WHERE dev=?1").bind(dev, until).run();
    return out({ ok: true });
  }
  if (b.action === "grantEmail") {
    const email = normEmail(b.email), days = Math.max(1, +b.days || 30);
    const a = await env.DB.prepare("SELECT until FROM accounts WHERE email=?1").bind(email).first();
    if (!a) return err("حسابی با این ایمیل نیست.", 404);
    const until = Math.max(Date.now(), a.until || 0) + days * 86400000;
    await env.DB.prepare("UPDATE accounts SET until=?2 WHERE email=?1").bind(email, until).run();
    const price = Math.max(0, +b.price || 0);
    if (price > 0) await env.DB.prepare("INSERT INTO codes(code,days,used_by,used_at,made_at,price,note) VALUES(?1,?2,?3,?4,?4,?5,?6)").bind("MAN-" + newCode(), days, "e:" + email, Date.now(), price, String(b.note || email).slice(0, 80)).run();
    return out({ until });
  }
  if (b.action === "ban") {
    const k = b.email ? "e:" + normEmail(b.email) : String(b.device || "").trim();
    if (!k) return err("چیزی انتخاب نشده", 400);
    if (b.on) await env.DB.prepare("INSERT INTO banned(k,why,at) VALUES(?1,?2,?3) ON CONFLICT(k) DO UPDATE SET why=?2").bind(k, String(b.why || "").slice(0, 80), Date.now()).run();
    else await env.DB.prepare("DELETE FROM banned WHERE k=?1").bind(k).run();
    return out({ ok: true });
  }
  if (b.action === "stats") {
    const d = dayKey();
    const q = async (sql, ...a) => (await env.DB.prepare(sql).bind(...a).first()) || {};
    return out({
      devicesToday: (await q("SELECT COUNT(DISTINCT dev) c FROM usage WHERE per=?1 AND dev NOT LIKE 'ip:%'", d)).c,
      activeSubs: (await q("SELECT COUNT(*) c FROM sub WHERE until>?1", Date.now())).c,
      unusedCodes: (await q("SELECT COUNT(*) c FROM codes WHERE used_by IS NULL")).c,
      today: (await env.DB.prepare("SELECT kind, SUM(n) s FROM usage WHERE per=?1 AND dev NOT LIKE 'ip:%' GROUP BY kind").bind(d).all()).results,
      refs: await q("SELECT COUNT(*) n, COALESCE(SUM(rewarded),0) r FROM refs"),
      devTotal: (await q("SELECT COUNT(*) c FROM devs")).c,
      devNew1: (await q("SELECT COUNT(*) c FROM devs WHERE first_seen>?1", Date.now() - 86400000)).c,
      devNew7: (await q("SELECT COUNT(*) c FROM devs WHERE first_seen>?1", Date.now() - 7 * 86400000)).c,
      devAct7: (await q("SELECT COUNT(*) c FROM devs WHERE last_seen>?1", Date.now() - 7 * 86400000)).c,
      accTotal: (await q("SELECT COUNT(*) c FROM accounts")).c,
      accNew7: (await q("SELECT COUNT(*) c FROM accounts WHERE created>?1", Date.now() - 7 * 86400000)).c,
      sales30: await q("SELECT COUNT(*) n, COALESCE(SUM(price),0) s FROM codes WHERE price>0 AND made_at>?1", Date.now() - 30 * 86400000),
      salesAll: await q("SELECT COUNT(*) n, COALESCE(SUM(price),0) s FROM codes WHERE price>0"),
      bySrc: (await env.DB.prepare("SELECT CASE WHEN code LIKE 'BZ-%' THEN 'bazaar' WHEN code LIKE 'MK-%' THEN 'myket' ELSE 'other' END src, COUNT(*) n, COALESCE(SUM(price),0) s, COALESCE(SUM(CASE WHEN made_at>?1 THEN 1 ELSE 0 END),0) n30, COALESCE(SUM(CASE WHEN made_at>?1 THEN price ELSE 0 END),0) s30 FROM codes WHERE price>0 GROUP BY src").bind(Date.now() - 30 * 86400000).all()).results,
      expiring: (await env.DB.prepare("SELECT dev, until FROM sub WHERE until>?1 AND until<?2 ORDER BY until LIMIT 30").bind(Date.now(), Date.now() + 7 * 86400000).all()).results,
    });
  }
  return err("دستور نامعتبر", 400);
}

const PRIVACY_HTML = `<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>سیاست حریم خصوصی — هوش مصنوعی هوشیار</title>
<style>body{font-family:system-ui,Tahoma,sans-serif;max-width:720px;margin:0 auto;padding:20px;line-height:2;color:#1c1b2e;background:#fafaff}h1{font-size:22px}h2{font-size:17px;margin-top:26px;color:#4b3fd1}li{margin:4px 0}small{color:#666}</style>
<h1>سیاست حریم خصوصی «هوش مصنوعی هوشیار»</h1>
<small>آخرین به‌روزرسانی: آبان ۱۴۰۵</small>
<h2>چه اطلاعاتی جمع‌آوری می‌شود</h2>
<ul>
<li><b>شناسه‌ی تصادفی دستگاه:</b> فقط برای اعمال سهمیه‌ی روزانه و بررسی اشتراک. به نام یا شماره‌ی شما وصل نیست.</li>
<li><b>ورودی بخش‌های هوش مصنوعی:</b> متن، صدا یا تصویری که برای چت، ساخت تصویر، ترجمه، صدا و مانند آن می‌فرستید، برای پردازش به سرور ما و سرویس‌های هوش مصنوعی طرف‌قرارداد ارسال می‌شود.</li>
<li><b>حساب کاربری (اختیاری):</b> فقط اگر برای خرید یا بازیابی اشتراک وارد شوید؛ شناسه‌ی حساب تلگرام (و نام کاربری آن) یا ایمیل شما برای ورود و نگه‌داری اشتراک ذخیره می‌شود.</li>
<li><b>اطلاعات خرید:</b> شناسه‌ی محصول و توکن خرید برای تأیید پرداخت و فعال‌سازی اشتراک.</li>
<li><b>کد اشتراک:</b> هنگام فعال‌سازی برای تأیید اعتبار ارسال می‌شود.</li>
</ul>
<h2>چه اطلاعاتی جمع‌آوری نمی‌شود</h2>
<ul>
<li>شماره‌ی تلفن، مخاطبین، پیامک یا موقعیت مکانی.</li>
<li>فایل‌هایی که در ابزارهای محلی استفاده می‌شوند (حذف اشیاء، اسکن و PDF، فشرده‌سازی، QR و کارت ویزیت، عکس پاسپورتی، بهبود کیفیت، ویرایش استوری)؛ این پردازش‌ها فقط روی گوشی شما انجام می‌شود.</li>
<li>تاریخچه‌ی چت‌ها و ساخته‌های شما روی سرور ما ذخیره نمی‌شود؛ فقط روی خود گوشی می‌ماند.</li>
</ul>
<h2>دسترسی‌ها</h2>
<ul><li><b>میکروفون:</b> فقط هنگام استفاده از چت صوتی و ابزارهای صدا.</li><li><b>دوربین:</b> فقط هنگام گرفتن عکس برای ابزارها.</li><li><b>ذخیره‌سازی:</b> برای ذخیره‌ی خروجی‌ها در گالری و پوشه‌ی دانلود.</li><li><b>اعلان:</b> برای یادآور برنامه‌ریز.</li></ul>
<h2>اشخاص ثالث</h2>
<p>درخواست‌های هوش مصنوعی ممکن است توسط ارائه‌دهندگانی مانند Google، Mistral، Groq و سرویس‌های مشابه پردازش شوند و تابع سیاست‌های آن‌ها هستند. پرداخت‌ها از طریق کافه‌بازار (یا مارکت‌های دیگر) انجام می‌شود و اطلاعات کارت بانکی شما در اختیار ما قرار نمی‌گیرد.</p>
<h2>حذف اطلاعات</h2>
<p>برای حذف شناسه‌ی دستگاه یا حساب خود از سرور، از بخش «تماس با ما» داخل اپ پیام بدهید.</p>
<h2>تماس</h2>
<p>سازنده: نوید سالاری — از بخش «تماس با ما» داخل اپ یا از طریق تلگرام: <a href="https://t.me/na112233vi">@na112233vi</a></p>
</html>`;
const devSeen = new Map();
// ───────── مسیریابی ─────────
export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (!env.DB) return err("پایگاه داده (D1) به Worker وصل نشده است.", 500);
      await init(env);
      if (url.pathname === "/" ) return new Response("Hooshyar server OK", { headers: { "content-type": "text/plain" } });
      if (url.pathname === "/tg" && req.method === "POST") return await tgWebhook(req, env);
      if (url.pathname === "/privacy") return new Response(PRIVACY_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=3600" } });
      if (url.pathname !== "/" && !url.pathname.startsWith("/v1/")) return await admin(req, env, url);
      const dev = deviceOf(req);
      if (!dev) return err("شناسه‌ی دستگاه نامعتبر است.", 401);
      {
        const nowT = Date.now(), seen = devSeen.get(dev) || 0;
        if (nowT - seen > 1800000) {
          devSeen.set(dev, nowT);
          if (devSeen.size > 5000) devSeen.clear();
          await env.DB.prepare("INSERT INTO devs(dev,first_seen,last_seen) VALUES(?1,?2,?2) ON CONFLICT(dev) DO UPDATE SET last_seen=?2").bind(dev, nowT).run();
        }
        const bn = await env.DB.prepare("SELECT 1 x FROM banned WHERE k=?1 OR k=(SELECT 'e:'||email FROM acc_dev WHERE dev=?1)").bind(dev).first();
        if (bn) return err("دسترسی این حساب مسدود شده است. با پشتیبانی تماس بگیر.", 403);
      }
      if (url.pathname === "/v1/me") return await me(env, dev);
      if (url.pathname === "/v1/models") return out({ data: [...GROQ_OK].map((id) => ({ id })) });
      if (url.pathname === "/v1/redeem" && req.method === "POST") return await redeem(req, env, dev);
      if (url.pathname === "/v1/auth/request" && req.method === "POST") return await authRequest(req, env, dev);
      if (url.pathname === "/v1/auth/verify" && req.method === "POST") return await authVerify(req, env, dev);
      if (url.pathname === "/v1/pay/bazaar" && req.method === "POST") return await payBazaar(req, env, dev);
      if (url.pathname === "/v1/pay/myket" && req.method === "POST") return await payMyket(req, env, dev);
      if (url.pathname === "/v1/auth/tg/start" && req.method === "POST") return await tgStart(req, env, dev);
      if (url.pathname === "/v1/auth/tg/check" && req.method === "POST") return await tgCheck(req, env, dev);
      if (url.pathname === "/v1/auth/me") return await authMe(env, dev);
      if (url.pathname === "/v1/auth/logout" && req.method === "POST") return await authOut(env, dev);
      if (url.pathname === "/v1/ref") return await refInfo(env, dev);
      if (url.pathname === "/v1/ref/use" && req.method === "POST") return await refUse(req, env, dev);
      if (url.pathname === "/v1/enhance" && req.method === "POST") {
        const until = await subOf(env, dev);
        return await enhanceAi(req, env, dev, until, limits(env, !!until));
      }
      if (url.pathname === "/v1/avatar" && req.method === "POST") {
        const until = await subOf(env, dev);
        return await avatar(req, env, dev, until, limits(env, !!until));
      }
      const kind = { "/v1/chat/completions": "chat", "/v1/audio/transcriptions": "stt", "/v1/tts": "tts", "/v1/music": "music", "/v1/image": "image" }[url.pathname];
      if (!kind || req.method !== "POST") return err("مسیر ناشناخته", 404);
      if (kind === "music") {
        const until = await subOf(env, dev);
        return await music(req, env, dev, until, limits(env, !!until));
      }
      const g = await guard(env, req, dev, kind);
      if (g instanceof Response) return g;
      if (kind === "chat") return await chat(req, env, g.until);
      if (kind === "stt") return await stt(req, env);
      if (kind === "image") return await image(req, env, dev);
      return await tts(req, env, dev, g.until, g.L);
    } catch (e) {
      return err("خطای سرور: " + String(e && e.message ? e.message : e).slice(0, 120), 500);
    }
  },
};

const ADMIN_HTML = String.raw`<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>پنل مدیریت هوشیار</title>
<link rel="manifest" href="__BASE__/manifest.json"><link rel="icon" href="__BASE__/icon.svg"><meta name="theme-color" content="#0f1020">
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{font-family:system-ui,Tahoma,sans-serif;max-width:640px;margin:0 auto;padding:16px;background:#0f1020;color:#eceaff;line-height:1.7}
h1{font-size:20px;margin:4px 0 14px}h2{font-size:15px;margin:0 0 10px;color:#b9adff}
.card{background:#181a33;border:1px solid #2b2e57;border-radius:16px;padding:14px;margin-bottom:14px}
input,select,button{font:inherit;width:100%;padding:11px;margin:4px 0;border-radius:12px;border:1px solid #3a3e75;background:#10122a;color:#eceaff}
button{background:linear-gradient(90deg,#7c6cff,#3fa2f7);border:0;font-weight:700;cursor:pointer}
button.s{background:#262a55;font-weight:400}label{display:block;font-size:12px;opacity:.75;margin-top:8px}
.row{display:flex;gap:8px}.row>*{flex:1}
.code{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:8px 10px;margin:5px 0;background:#10122a;border-radius:10px;font-family:monospace;direction:ltr}
.code small{font-family:system-ui;direction:rtl;opacity:.7}.used{opacity:.45;text-decoration:line-through}
#msg{position:sticky;top:6px;z-index:5;background:#3a2b6e;border-radius:10px;padding:8px 12px;margin-bottom:10px;display:none}
</style>
<h1>🛠 پنل مدیریت هوشیار</h1>
<div id=msg></div>
<div class=card>
  <h2>ورود</h2>
  <input id=t type=password placeholder="رمز مدیر (ADMIN_TOKEN)" autocomplete="current-password">
  <button class=s onclick="stats()">ورود و نمایش آمار</button>
  <div id=st style="margin-top:8px;font-size:14px"></div>
</div>
<div class=card>
  <h2>ساخت کد اشتراک</h2>
  <div class=row>
    <div><label>پلن (قیمت به هزار تومان)</label><select id=d onchange="$('pr').value=PR[this.selectedIndex]"></select></div>
    <div><label>تعداد کد</label><input id=n type=number value=1 min=1 max=100></div>
  </div>
  <div class=row>
    <div><label>مبلغ دریافتی (هزار تومان)</label><input id=pr type=number min=0 value=300></div>
    <div><label>یادداشت (نام خریدار)</label><input id=nt maxlength=80></div>
  </div>
  <button onclick="make()">بساز</button>
  <div id=made></div>
  <button class=s id=copyAll style="display:none" onclick="copyAll()">کپی همه‌ی کدها</button>
</div>
<div class=card>
  <h2>پرداخت کافه‌بازار</h2>
  <label>کد دریافتی از بازار (بعد از تأیید دسترسی، از آدرس مرورگر)</label>
  <input id=bzc style="direction:ltr" placeholder="code">
  <button class=s onclick="bzEx()">تبدیل به refresh token</button>
  <button class=s onclick="bzCk()">تست اتصال به API بازار</button>
  <button class=s onclick="tgTs()">ارسال پیام آزمایشی به تلگرام من</button>
  <div id=bzo style="margin-top:8px;font-size:13px;word-break:break-all;direction:ltr"></div>
</div>
<div class=card>
  <h2>ربات تلگرام (ورود کاربران)</h2>
  <button class=s onclick="tgSetup()">فعال‌سازی / بازنشانی ربات</button>
  <div id=tgs style="margin-top:8px;font-size:14px"></div>
</div>
<div class=card>
  <h2>کاربران</h2>
  <input id=uq placeholder="جستجو: ایمیل یا شناسه‌ی گوشی (خالی = جدیدترین‌ها)" style="direction:ltr">
  <button class=s onclick="users()">نمایش / جستجو</button>
  <div id=ul></div>
  <div id=ud></div>
</div>
<div class=card>
  <h2>آخرین کدها</h2>
  <button class=s onclick="list()">نمایش / تازه‌سازی</button>
  <div id=lst></div>
</div>
<div class=card>
  <h2>تست سرویس‌های هوش مصنوعی</h2>
  <button class=s onclick="diag()">تست کلیدها</button>
  <div id=dg style="margin-top:8px;font-size:14px"></div>
</div>
<div class=card>
  <h2>فعال‌سازی دستی یک گوشی</h2>
  <input id=dev placeholder="شناسه‌ی گوشی کاربر (از تنظیمات اپ)" style="direction:ltr">
  <select id=gd onchange="$('gp').value=PR[this.selectedIndex]"></select>
  <div class=row>
    <input id=gp type=number min=0 value=300 placeholder="مبلغ دریافتی (هزار تومان؛ ۰ = ثبت نشود)">
    <input id=gn maxlength=80 placeholder="یادداشت">
  </div>
  <button onclick="grant()">فعال کن</button>
</div>
<script>
const $=id=>document.getElementById(id);let last=[];
const PR=[300,550,800,1000,1200,1400,1600,1800,2200,2400,2600,2800];
const lab=m=>(m==12?'۱۲ ماه (یک سال)':m+' ماه')+' — '+PR[m-1];
for(const sel of [$('d'),$('gd')])for(let m=1;m<=12;m++){const o=document.createElement('option');o.value=m==12?365:m*30;o.textContent=lab(m);sel.appendChild(o)}
$('pr').value=PR[0];$('gp').value=PR[0];
const toman=k=>k>=1000?(k/1000).toLocaleString('fa-IR',{maximumFractionDigits:2})+' میلیون':Number(k).toLocaleString('fa-IR')+' هزار';
$('t').value=localStorage.getItem('adm')||'';
function say(x){const m=$('msg');m.textContent=x;m.style.display='block';clearTimeout(say.k);say.k=setTimeout(()=>m.style.display='none',4000)}
async function api(b){localStorage.setItem('adm',$('t').value);
  const r=await fetch(location.pathname.replace(/\/+$/,'')+'/api',{method:'POST',headers:{'x-admin':$('t').value,'content-type':'application/json'},body:JSON.stringify(b)});
  const j=await r.json();if(!r.ok)throw new Error(j.error&&j.error.message||'خطا');return j}
function dayLabel(days){return days>=365?'۱۲ ماه':Math.round(days/30)+' ماه'}
async function stats(){try{const j=await api({action:'stats'});
  const k={chat:'چت',tts:'صدا',stt:'گفتار',music:'آهنگ'};
  $('st').innerHTML='کل گوشی‌ها: <b>'+j.devTotal+'</b> • جدید ۲۴ساعت: <b>'+j.devNew1+'</b> • جدید ۷ روز: <b>'+j.devNew7+'</b> • فعال ۷ روز: <b>'+j.devAct7+'</b><br>حساب‌های ایمیلی: <b>'+j.accTotal+'</b> • جدید ۷ روز: <b>'+j.accNew7+'</b><br>فروش ۳۰ روز اخیر: <b>'+j.sales30.n+' اشتراک • '+toman(j.sales30.s)+' تومان</b><br>کل فروش ثبت‌شده: <b>'+j.salesAll.n+' اشتراک • '+toman(j.salesAll.s)+' تومان</b><br>'+srcLine(j.bySrc)+'<br>دعوت دوستان: <b>'+j.refs.n+' دعوت • '+j.refs.r+' جایزه‌ی پرداخت‌شده</b><br>دستگاه‌های فعال امروز: <b>'+j.devicesToday+'</b><br>اشتراک‌های فعال: <b>'+j.activeSubs+'</b><br>کدهای استفاده‌نشده: <b>'+j.unusedCodes+'</b><br>مصرف امروز: '+(j.today||[]).filter(x=>k[x.kind]).map(x=>k[x.kind]+' '+x.s).join(' • ')+
  '<br><br>اشتراک‌هایی که تا ۷ روز دیگر تمام می‌شوند ('+(j.expiring||[]).length+'):'+(j.expiring||[]).map(x=>'<div class=code><span style="font-size:11px">'+x.dev.slice(0,18)+'…</span><small>'+new Date(x.until).toLocaleDateString('fa-IR')+'</small></div>').join('');
  }catch(e){say(e.message)}}
async function make(){try{const j=await api({action:'codes',count:$('n').value,days:$('d').value,price:$('pr').value,note:$('nt').value});last=j.codes;
  $('made').innerHTML=j.codes.map(c=>'<div class=code><span>'+c+'</span><small>'+dayLabel(j.days)+'</small><button class=s style="width:auto;padding:4px 12px" onclick="cp(\''+c+'\')">کپی</button><button class=s style="width:auto;padding:4px 12px" onclick="cp(msgFor(\''+c+'\','+j.days+'))">پیام آماده</button></div>').join('');
  $('copyAll').style.display=j.codes.length>1?'block':'none';say('ساخته شد ✅')}catch(e){say(e.message)}}
function msgFor(c,d){return 'سلام 🌹 ممنون از خریدت!\nکد اشتراک '+dayLabel(d)+'ه‌ی هوش مصنوعی هوشیار:\n\n'+c+'\n\nروش فعال‌سازی: داخل اپ ← منوی کناری ← تنظیمات ← «کد اشتراک» را وارد کن و «فعال‌سازی اشتراک» را بزن.'}
function cp(t){navigator.clipboard.writeText(t).then(()=>say('کپی شد'))}
function copyAll(){cp(last.join('\n'))}
async function list(){try{const j=await api({action:'list'});
  $('lst').innerHTML=j.codes.map(c=>'<div class="code '+(c.used_by?'used':'')+'"><span>'+c.code+'</span><small>'+dayLabel(c.days)+(c.price?' • '+c.price+'هزار':'')+(c.note?' • '+c.note:'')+(c.used_by?' • استفاده شد':' • آزاد')+'</small>'+(c.used_by?'':'<button class=s style="width:auto;padding:4px 12px" onclick="revoke(\''+c.code+'\')">لغو</button>')+'</div>').join('')||'<small>هنوز کدی نساخته‌ای</small>'}catch(e){say(e.message)}}
const fd=ms=>ms?new Date(ms).toLocaleDateString('fa-IR')+' '+new Date(ms).toLocaleTimeString('fa-IR',{hour:'2-digit',minute:'2-digit'}):'—';
const subTxt=u=>u&&u>Date.now()?'✅ فعال تا '+new Date(u).toLocaleDateString('fa-IR')+' ('+Math.ceil((u-Date.now())/86400000)+' روز)':'رایگان';
const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function users(){try{const j=await api({action:'users',q:$('uq').value});
  $('ul').innerHTML=(j.accounts.length?'<small>حساب‌های ایمیلی</small>':'')+j.accounts.map(a=>'<div class=code style="cursor:pointer" data-e="'+esc(a.email)+'" onclick="openUser(this.dataset.e)"><span style="font-size:12px">'+esc(a.note||a.email)+'</span><small>'+subTxt(a.until)+' • '+a.devices+' گوشی</small></div>').join('')
   +(j.devices.length?'<small>گوشی‌ها</small>':'')+j.devices.map(d=>'<div class=code style="cursor:pointer" data-d="'+esc(d.dev)+'" onclick="openDev(this.dataset.d)"><span style="font-size:11px">'+esc(d.dev.slice(0,20))+'…</span><small>'+(d.email?esc(d.email)+' • ':'')+'آخرین بازدید '+fd(d.last)+'</small></div>').join('')
   ||'<small>چیزی پیدا نشد</small>'}catch(e){say(e.message)}}
let cur=null;
async function openUser(e){cur={email:e};await showUser({action:'user',email:e})}
async function openDev(d){cur={device:d};await showUser({action:'user',device:d})}
async function showUser(req){try{const j=await api(req);const A=j.account;const names={chat:'چت',tts:'صدا',stt:'گفتار',image:'تصویر',music:'آهنگ',avatar:'آواتار',enhance:'بهبود',all:'کل'};
  let h='<div class=card style="background:#10122a">';
  if(A)h+='<b>'+esc(A.email)+'</b>'+(A.note?' • '+esc(A.note):'')+'<br>ثبت‌نام: '+fd(A.created)+'<br>آخرین ورود: '+fd(A.last_login)+'<br>اشتراک حساب: '+subTxt(A.until)+(j.bannedEmail?'<br>🚫 مسدود ('+esc(j.bannedEmail)+')':'')+'<br>';
  h+=j.devices.map(d=>'<hr style="border-color:#2b2e57"><small>گوشی: '+esc(d.dev)+'</small><br>اولین بازدید: '+fd(d.first)+' • آخرین: '+fd(d.last)+'<br>اشتراک گوشی: '+subTxt(d.until)
    +'<br>مصرف کل: '+(d.total.filter(x=>names[x.kind]).map(x=>names[x.kind]+' '+x.s).join(' • ')||'—')
    +'<br>مصرف امروز: '+(d.today.filter(x=>names[x.kind]).map(x=>names[x.kind]+' '+x.n).join(' • ')||'—')
    +'<br>کد دعوت: '+esc(d.refCode||'—')+' • دعوت کرده: '+d.invited+' (جایزه '+d.rewarded+')'+(d.invitedBy?' • دعوت‌شده توسط یک گوشی':'')
    +(d.codes.length?'<br>کدهای فعال‌شده: '+d.codes.map(c=>esc(c.code)+(c.price?' ('+c.price+'هزار)':'')).join('، '):'')
    +(d.banned?'<br>🚫 مسدود ('+esc(d.banned)+')':'')).join('');
  h+='</div><div class=row><input id=ugd type=number value=30 min=1><input id=ugp type=number value=0 min=0 placeholder="مبلغ (هزار تومان)"></div><button onclick="uGrant()">افزودن روز اشتراک</button><div class=row><button class=s onclick="uSet(0)">لغو اشتراک</button><button class=s onclick="uBan(1)">مسدود</button><button class=s onclick="uBan(0)">رفع مسدودی</button></div>';
  $('ud').innerHTML=h}catch(e){say(e.message)}}
async function uGrant(){try{if(cur.email){const j=await api({action:'grantEmail',email:cur.email,days:$('ugd').value,price:$('ugp').value});say('اشتراک تا '+new Date(j.until).toLocaleDateString('fa-IR'));openUser(cur.email)}else{const j=await api({action:'grant',device:cur.device,days:$('ugd').value,price:$('ugp').value});say('فعال شد');openDev(cur.device)}}catch(e){say(e.message)}}
async function uSet(u){if(!confirm('اشتراک لغو شود؟'))return;try{await api({action:'setSub',email:cur.email,device:cur.device,until:u});say('لغو شد');cur.email?openUser(cur.email):openDev(cur.device)}catch(e){say(e.message)}}
async function uBan(on){try{await api({action:'ban',email:cur.email,device:cur.device,on:on,why:on?'توسط مدیر':''});say(on?'مسدود شد':'رفع مسدودی شد');cur.email?openUser(cur.email):openDev(cur.device)}catch(e){say(e.message)}}
async function bzEx(){try{const j=await api({action:'bazaarExchange',code:$('bzc').value});$('bzo').innerHTML='refresh token (فقط در Cloudflare بگذار: BAZAAR_REFRESH_TOKEN):<br><b>'+esc(j.refresh_token)+'</b>'}catch(e){say(e.message)}}
function srcLine(a){const m={bazaar:'کافه‌بازار',myket:'مایکت',other:'کد/تلگرام/دستی'};const r=['bazaar','myket','other'].map(k=>{const x=(a||[]).find(y=>y.src===k)||{n:0,s:0,n30:0,s30:0};return m[k]+': <b>'+x.n+' خرید • '+toman(x.s)+' تومان</b> (۳۰ روز: '+x.n30+' • '+toman(x.s30)+')'});return r.join('<br>')}
async function tgTs(){try{await api({action:'tgTest'});say('پیام آزمایشی فرستاده شد؛ تلگرام را نگاه کن ✅')}catch(e){say(e.message)}}
async function bzCk(){try{await api({action:'bazaarCheck'});$('bzo').textContent='✅ اتصال به API بازار برقرار است'}catch(e){$('bzo').textContent='❌ '+e.message}}
async function tgSetup(){try{const j=await api({action:'tgSetup'});$('tgs').textContent=j.ok?('✅ ربات @'+j.bot+' فعال شد'):('❌ '+j.desc)}catch(e){say(e.message)}}
async function revoke(c){if(!confirm('این کد آزاد لغو شود؟'))return;try{const j=await api({action:'revoke',code:c});say(j.ok?'لغو شد':'پیدا نشد');list()}catch(e){say(e.message)}}
async function diag(){try{$('dg').textContent='در حال تست…';const j=await api({action:'diag'});
  $('dg').innerHTML=j.results.map(x=>(x.ok?'✅ ':'❌ ')+x.name+(x.n?' #'+x.n:'')+' — '+x.status+(x.model?' ('+x.model+')':'')).join('<br>')+'<br><small>۲۰۰=سالم • ۴۰۱=کلید اشتباه • ۴۲۹=سهمیه پر • ۴۰۳=مسدود</small>'}catch(e){say(e.message)}}
async function grant(){try{const j=await api({action:'grant',device:$('dev').value,days:$('gd').value,price:$('gp').value,note:$('gn').value});
  say('فعال شد تا '+new Date(j.until).toLocaleDateString('fa-IR'))}catch(e){say(e.message)}}
</script></html>`;
