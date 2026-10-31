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
      }
    : {
        chat: num(env.FREE_CHAT, 30),
        stt: num(env.FREE_STT, 10),
        tts: num(env.FREE_TTS, 15),
        image: num(env.FREE_IMAGE, 6),
        elchars: 0,
        music: 0,
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
  return r && r.until > Date.now() ? r.until : 0;
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
  { name: "groq", base: "https://api.groq.com/openai/v1", env: "GROQ_KEY", model: "llama-3.3-70b-versatile", vision: "meta-llama/llama-4-scout-17b-16e-instruct" },
  { name: "cerebras", base: "https://api.cerebras.ai/v1", env: "CEREBRAS_KEY", model: "gpt-oss-120b" },
  { name: "mistral", base: "https://api.mistral.ai/v1", env: "MISTRAL_KEY", model: "mistral-small-latest", vision: "mistral-small-latest" },
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
  const hasImage = JSON.stringify(body.messages || []).includes('"image_url"');
  const cap = until ? 6000 : 3000;
  if (!body.max_tokens || body.max_tokens > cap) body.max_tokens = cap;
  const attempts = [];
  for (const p of CHAIN) {
    if (hasImage && !p.vision) continue;
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
  if (!audio) {
    try { audio = await edgeTts(text, lang, male); }
    catch (e) { fails.push(String(e.message || e)); }
  }
  if (!audio) return err("ساخت صدا ممکن نشد (" + fails.join(" | ") + ")", 502);
  return new Response(audio, { headers: { "content-type": "audio/mpeg", "x-voice-source": src } });
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
  return until;
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
      await env.DB.prepare("INSERT INTO codes(code,days,made_at) VALUES(?1,?2,?3)").bind(c, days, Date.now()).run();
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
    const r = await env.DB.prepare("SELECT code,days,used_by,used_at,made_at FROM codes ORDER BY made_at DESC LIMIT 80").all();
    return out({ codes: r.results });
  }
  if (b.action === "grant") {
    const dev = String(b.device || "").trim();
    if (!/^[A-Za-z0-9-]{16,64}$/.test(dev)) return err("شناسه‌ی دستگاه نامعتبر", 400);
    return out({ until: await grant(env, dev, Math.max(+b.days || 30, 1)) });
  }
  if (b.action === "stats") {
    const d = dayKey();
    const q = async (sql, ...a) => (await env.DB.prepare(sql).bind(...a).first()) || {};
    return out({
      devicesToday: (await q("SELECT COUNT(DISTINCT dev) c FROM usage WHERE per=?1 AND dev NOT LIKE 'ip:%'", d)).c,
      activeSubs: (await q("SELECT COUNT(*) c FROM sub WHERE until>?1", Date.now())).c,
      unusedCodes: (await q("SELECT COUNT(*) c FROM codes WHERE used_by IS NULL")).c,
      today: (await env.DB.prepare("SELECT kind, SUM(n) s FROM usage WHERE per=?1 AND dev NOT LIKE 'ip:%' GROUP BY kind").bind(d).all()).results,
    });
  }
  return err("دستور نامعتبر", 400);
}

// ───────── مسیریابی ─────────
export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (!env.DB) return err("پایگاه داده (D1) به Worker وصل نشده است.", 500);
      await init(env);
      if (url.pathname === "/" ) return new Response("Hooshyar server OK", { headers: { "content-type": "text/plain" } });
      if (url.pathname !== "/" && !url.pathname.startsWith("/v1/")) return await admin(req, env, url);
      const dev = deviceOf(req);
      if (!dev) return err("شناسه‌ی دستگاه نامعتبر است.", 401);
      if (url.pathname === "/v1/me") return await me(env, dev);
      if (url.pathname === "/v1/models") return out({ data: [...GROQ_OK].map((id) => ({ id })) });
      if (url.pathname === "/v1/redeem" && req.method === "POST") return await redeem(req, env, dev);
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
    <div><label>مدت اشتراک</label><select id=d></select></div>
    <div><label>تعداد کد</label><input id=n type=number value=1 min=1 max=100></div>
  </div>
  <button onclick="make()">بساز</button>
  <div id=made></div>
  <button class=s id=copyAll style="display:none" onclick="copyAll()">کپی همه‌ی کدها</button>
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
  <select id=gd></select>
  <button onclick="grant()">فعال کن</button>
</div>
<script>
const $=id=>document.getElementById(id);let last=[];
const lab=m=>m==12?'۱۲ ماه (یک سال)':m+' ماه';
for(const sel of [$('d'),$('gd')])for(let m=1;m<=12;m++){const o=document.createElement('option');o.value=m==12?365:m*30;o.textContent=lab(m);sel.appendChild(o)}
$('t').value=localStorage.getItem('adm')||'';
function say(x){const m=$('msg');m.textContent=x;m.style.display='block';clearTimeout(say.k);say.k=setTimeout(()=>m.style.display='none',4000)}
async function api(b){localStorage.setItem('adm',$('t').value);
  const r=await fetch(location.pathname.replace(/\/+$/,'')+'/api',{method:'POST',headers:{'x-admin':$('t').value,'content-type':'application/json'},body:JSON.stringify(b)});
  const j=await r.json();if(!r.ok)throw new Error(j.error&&j.error.message||'خطا');return j}
function dayLabel(days){return days>=365?'۱۲ ماه':Math.round(days/30)+' ماه'}
async function stats(){try{const j=await api({action:'stats'});
  const k={chat:'چت',tts:'صدا',stt:'گفتار',music:'آهنگ'};
  $('st').innerHTML='دستگاه‌های فعال امروز: <b>'+j.devicesToday+'</b><br>اشتراک‌های فعال: <b>'+j.activeSubs+'</b><br>کدهای استفاده‌نشده: <b>'+j.unusedCodes+'</b><br>مصرف امروز: '+(j.today||[]).filter(x=>k[x.kind]).map(x=>k[x.kind]+' '+x.s).join(' • ');
  }catch(e){say(e.message)}}
async function make(){try{const j=await api({action:'codes',count:$('n').value,days:$('d').value});last=j.codes;
  $('made').innerHTML=j.codes.map(c=>'<div class=code><span>'+c+'</span><small>'+dayLabel(j.days)+'</small><button class=s style="width:auto;padding:4px 12px" onclick="cp(\''+c+'\')">کپی</button></div>').join('');
  $('copyAll').style.display=j.codes.length>1?'block':'none';say('ساخته شد ✅')}catch(e){say(e.message)}}
function cp(t){navigator.clipboard.writeText(t).then(()=>say('کپی شد'))}
function copyAll(){cp(last.join('\n'))}
async function list(){try{const j=await api({action:'list'});
  $('lst').innerHTML=j.codes.map(c=>'<div class="code '+(c.used_by?'used':'')+'"><span>'+c.code+'</span><small>'+dayLabel(c.days)+(c.used_by?' • استفاده شد':' • آزاد')+'</small></div>').join('')||'<small>هنوز کدی نساخته‌ای</small>'}catch(e){say(e.message)}}
async function diag(){try{$('dg').textContent='در حال تست…';const j=await api({action:'diag'});
  $('dg').innerHTML=j.results.map(x=>(x.ok?'✅ ':'❌ ')+x.name+(x.n?' #'+x.n:'')+' — '+x.status+(x.model?' ('+x.model+')':'')).join('<br>')+'<br><small>۲۰۰=سالم • ۴۰۱=کلید اشتباه • ۴۲۹=سهمیه پر • ۴۰۳=مسدود</small>'}catch(e){say(e.message)}}
async function grant(){try{const j=await api({action:'grant',device:$('dev').value,days:$('gd').value});
  say('فعال شد تا '+new Date(j.until).toLocaleDateString('fa-IR'))}catch(e){say(e.message)}}
</script></html>`;
