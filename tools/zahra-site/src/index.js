import STATIC from './static.js';

// Zahra SHEIN quote site.
// Customers paste a shared SHEIN cart link; the owner's reader phone opens it,
// sends back the items, and this worker prices them in Iraqi dinar.

const DEFAULTS = { rate: 1520, depositPct: 10, serviceOn: '1', notice: '' };
const ROUND_TO = 250;
const JOB_TTL_MS = 7 * 24 * 3600 * 1000;
const PER_IP_PER_HOUR = 20;
const ADMIN_SESSION_MS = 30 * 24 * 3600 * 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    try {
      if (!path.startsWith('/api/')) return serveStatic(path);

      // ----- customers -----
      if (path === '/api/public-settings' && request.method === 'GET') return publicSettings(env);
      if (path === '/api/quote' && request.method === 'POST') return createQuote(request, env);
      const q = path.match(/^\/api\/quote\/([a-z0-9]{12,40})$/);
      if (q && request.method === 'GET') return getQuote(env, q[1]);

      // ----- owner -----
      if (path === '/api/admin/state') return json({ hasPassword: !!(await getSetting(env, 'adminHash')) });
      if (path === '/api/admin/setup' && request.method === 'POST') return adminSetup(request, env);
      if (path === '/api/admin/login' && request.method === 'POST') return adminLogin(request, env);
      if (path.startsWith('/api/admin/')) {
        if (!(await isAdmin(request, env))) return json({ error: 'unauthorized' }, 401);
        if (path === '/api/admin/settings' && request.method === 'GET') return adminSettings(env, url);
        if (path === '/api/admin/settings' && request.method === 'POST') return saveSettings(request, env);
        if (path === '/api/admin/firebase' && request.method === 'POST') return saveFirebase(request, env);
        if (path === '/api/admin/password' && request.method === 'POST') return changePassword(request, env);
        if (path === '/api/admin/pairing/reset' && request.method === 'POST') return resetPairing(env, url);
        if (path === '/api/admin/test-push' && request.method === 'POST') return testPush(env);
      }

      // ----- reader phone -----
      if (path.startsWith('/api/reader/')) {
        if (!(await isReader(request, env))) return json({ error: 'unauthorized' }, 401);
        if (path === '/api/reader/config') return readerConfig(env);
        if (path === '/api/reader/register' && request.method === 'POST') return readerRegister(request, env);
        if (path === '/api/reader/pending') return readerPending(env);
        const r = path.match(/^\/api\/reader\/job\/([a-z0-9]{12,40})$/);
        if (r && request.method === 'GET') return readerJob(env, r[1]);
        if (r && request.method === 'POST') return readerResult(request, env, r[1]);
      }
      return json({ error: 'not found' }, 404);
    } catch (e) {
      return json({ error: 'server', detail: String(e && e.message || e) }, 500);
    }
  }
};

// ---------- pages ----------

const PAGE_ALIASES = { '/': '/index.html', '/admin': '/admin.html', '/admin/': '/admin.html', '/pair': '/pair.html' };

function serveStatic(path) {
  const f = STATIC[PAGE_ALIASES[path] || path];
  if (!f) return new Response('Not found', { status: 404 });
  const bytes = Uint8Array.from(atob(f.b64), c => c.charCodeAt(0));
  const isHtml = f.type.startsWith('text/html');
  return new Response(bytes, {
    headers: {
      'content-type': f.type,
      'cache-control': isHtml ? 'no-cache' : 'public, max-age=3600',
      'x-content-type-options': 'nosniff',
      'referrer-policy': 'strict-origin-when-cross-origin'
    }
  });
}

// ---------- helpers ----------

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }
  });
}

async function getSetting(env, k) {
  const row = await env.DB.prepare('SELECT v FROM settings WHERE k = ?').bind(k).first();
  return row ? row.v : null;
}

async function setSetting(env, k, v) {
  await env.DB.prepare('INSERT INTO settings (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v').bind(k, String(v)).run();
}

async function getConfig(env) {
  const rows = await env.DB.prepare("SELECT k, v FROM settings WHERE k IN ('rate','depositPct','serviceOn','notice','readerToken','readerSeen')").all();
  const c = { ...DEFAULTS };
  for (const r of rows.results || []) c[r.k] = r.v;
  c.rate = Number(c.rate) || DEFAULTS.rate;
  c.depositPct = Number(c.depositPct);
  if (!isFinite(c.depositPct) || c.depositPct < 0) c.depositPct = DEFAULTS.depositPct;
  return c;
}

function randomId(bytes = 16) {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return [...a].map(b => b.toString(16).padStart(2, '0')).join('');
}

function b64url(bytes) {
  let s = typeof bytes === 'string' ? btoa(unescape(encodeURIComponent(bytes))) : btoa(String.fromCharCode(...new Uint8Array(bytes)));
  return s.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function hmac(keyText, data) {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(keyText), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return b64url(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data)));
}

async function hashPassword(password, saltHex) {
  const salt = saltHex || randomId(16);
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: new TextEncoder().encode(salt), iterations: 100000 }, key, 256);
  return salt + ':' + b64url(bits);
}

function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

function bearer(request) {
  const h = request.headers.get('authorization') || '';
  return h.startsWith('Bearer ') ? h.slice(7).trim() : '';
}

function ceilTo(x, step) { return Math.ceil(x / step - 1e-9) * step; }

// Only links that lead to SHEIN are accepted, and only http(s).
function cleanCartUrl(text) {
  const m = String(text || '').match(/https?:\/\/[^\s"'<>]+/);
  if (!m) return null;
  let u;
  try { u = new URL(m[0]); } catch (e) { return null; }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
  if (!(u.hostname === 'shein.com' || u.hostname.endsWith('.shein.com'))) return null;
  u.protocol = 'https:';
  return u.toString();
}

// ---------- customers ----------

async function publicSettings(env) {
  const c = await getConfig(env);
  return json({ rate: c.rate, depositPct: c.depositPct, serviceOn: c.serviceOn === '1', notice: c.notice || '', roundTo: ROUND_TO });
}

async function createQuote(request, env) {
  const c = await getConfig(env);
  if (c.serviceOn !== '1') return json({ error: 'off', message: c.notice || 'الخدمة متوقفة مؤقتاً.' }, 503);
  let body = {};
  try { body = await request.json(); } catch (e) {}
  const url = cleanCartUrl(body.url);
  if (!url) return json({ error: 'bad-url', message: 'الصق رابط سلة شي ان الذي يبدأ بـ https.' }, 400);

  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  const now = Date.now();
  const recent = await env.DB.prepare('SELECT COUNT(*) AS n FROM jobs WHERE ip = ? AND created > ?').bind(ip, now - 3600 * 1000).first();
  if (recent && recent.n >= PER_IP_PER_HOUR) return json({ error: 'limit', message: 'طلبات كثيرة خلال ساعة. حاول لاحقاً.' }, 429);

  // Reuse a fresh result for the same link (last 10 minutes) instead of reading it again.
  const same = await env.DB.prepare("SELECT id FROM jobs WHERE url = ? AND status IN ('pending','working','done') AND created > ? ORDER BY created DESC LIMIT 1")
    .bind(url, now - 10 * 60 * 1000).first();
  if (same) return json({ id: same.id });

  const id = randomId(12);
  await env.DB.prepare('INSERT INTO jobs (id, url, status, created, updated, ip) VALUES (?, ?, ?, ?, ?, ?)')
    .bind(id, url, 'pending', now, now, ip).run();
  // Housekeeping: drop old jobs.
  await env.DB.prepare('DELETE FROM jobs WHERE created < ?').bind(now - JOB_TTL_MS).run();

  const pushed = await pushToReader(env, { job: id }).catch(e => ({ ok: false, error: String(e.message || e) }));
  if (!pushed.ok) {
    await env.DB.prepare("UPDATE jobs SET status = 'failed', error = ?, updated = ? WHERE id = ?")
      .bind('reader-unreachable: ' + (pushed.error || ''), Date.now(), id).run();
    return json({ error: 'reader', message: 'الخدمة غير متاحة الآن. حاول بعد قليل.' }, 503);
  }
  return json({ id });
}

async function getQuote(env, id) {
  const job = await env.DB.prepare('SELECT * FROM jobs WHERE id = ?').bind(id).first();
  if (!job) return json({ error: 'not-found' }, 404);
  const age = Date.now() - job.created;
  if ((job.status === 'pending' || job.status === 'working') && age > 90 * 1000) {
    await env.DB.prepare("UPDATE jobs SET status = 'failed', error = 'timeout', updated = ? WHERE id = ?").bind(Date.now(), id).run();
    return json({ status: 'failed', message: 'لم نتمكن من قراءة السلة الآن. حاول بعد قليل.' });
  }
  if (job.status === 'failed') {
    const msg = /empty/.test(job.error || '') ? 'لم نجد منتجات في هذا الرابط. تأكد أنه رابط مشاركة سلة من شي ان.'
      : 'لم نتمكن من قراءة السلة الآن. حاول بعد قليل.';
    return json({ status: 'failed', message: msg });
  }
  if (job.status !== 'done') return json({ status: job.status });
  const c = await getConfig(env);
  return json({ status: 'done', ...priceItems(JSON.parse(job.result || '[]'), c) });
}

function num(x) { const n = Number(x); return isFinite(n) ? n : 0; }

function priceItems(items, c) {
  const out = [];
  let usd = 0, count = 0;
  for (const it of items) {
    const qty = Math.max(1, Math.floor(num(it.qty) || 1));
    const unit = num(it.usd);
    const line = unit * qty;
    usd += line;
    count += qty;
    out.push({
      name: String(it.name || '').slice(0, 300),
      img: it.img ? (String(it.img).startsWith('//') ? 'https:' + it.img : String(it.img)) : '',
      attr: String(it.attr || '').slice(0, 120),
      qty,
      usd: round2(unit),
      usdBefore: num(it.usdBefore) > unit ? round2(num(it.usdBefore)) : null,
      lineUsd: round2(line),
      lineIqd: Math.round(line * c.rate),
      soldOut: !!it.soldOut
    });
  }
  const exactIqd = usd * c.rate;
  const totalIqd = ceilTo(exactIqd, ROUND_TO);
  const depositIqd = ceilTo(totalIqd * c.depositPct / 100, ROUND_TO);
  return { items: out, count, totalUsd: round2(usd), totalIqd, depositIqd, depositPct: c.depositPct, rate: c.rate };
}

function round2(x) { return Math.round(x * 100) / 100; }

// ---------- owner ----------

async function adminSetup(request, env) {
  if (await getSetting(env, 'adminHash')) return json({ error: 'exists' }, 409);
  const { password } = await request.json().catch(() => ({}));
  if (!password || String(password).length < 6) return json({ error: 'short', message: 'كلمة السر 6 أحرف على الأقل.' }, 400);
  await setSetting(env, 'adminHash', await hashPassword(String(password)));
  await setSetting(env, 'sessionKey', randomId(32));
  return issueSession(env);
}

async function adminLogin(request, env) {
  const stored = await getSetting(env, 'adminHash');
  if (!stored) return json({ error: 'no-password' }, 409);
  const { password } = await request.json().catch(() => ({}));
  const [salt] = stored.split(':');
  const candidate = await hashPassword(String(password || ''), salt);
  if (!safeEqual(candidate, stored)) {
    await new Promise(r => setTimeout(r, 600));
    return json({ error: 'wrong', message: 'كلمة السر غير صحيحة.' }, 401);
  }
  return issueSession(env);
}

async function issueSession(env) {
  const key = await getSetting(env, 'sessionKey');
  const exp = Date.now() + ADMIN_SESSION_MS;
  const token = exp + '.' + await hmac(key, 'admin.' + exp);
  return json({ token });
}

async function isAdmin(request, env) {
  const token = bearer(request);
  const [exp, sig] = token.split('.');
  if (!exp || !sig || Number(exp) < Date.now()) return false;
  const key = await getSetting(env, 'sessionKey');
  if (!key) return false;
  return safeEqual(sig, await hmac(key, 'admin.' + exp));
}

async function changePassword(request, env) {
  const { password } = await request.json().catch(() => ({}));
  if (!password || String(password).length < 6) return json({ error: 'short', message: 'كلمة السر 6 أحرف على الأقل.' }, 400);
  await setSetting(env, 'adminHash', await hashPassword(String(password)));
  await setSetting(env, 'sessionKey', randomId(32)); // signs out other devices
  return issueSession(env);
}

async function adminSettings(env, url) {
  const c = await getConfig(env);
  const fb = await getSetting(env, 'firebaseClient');
  const sa = await getSetting(env, 'firebaseServiceAccount');
  let token = c.readerToken;
  if (!token) { token = randomId(24); await setSetting(env, 'readerToken', token); }
  const fcm = await getSetting(env, 'readerFcm');
  const recent = await env.DB.prepare('SELECT status, COUNT(*) AS n FROM jobs WHERE created > ? GROUP BY status').bind(Date.now() - 24 * 3600 * 1000).all();
  return json({
    rate: c.rate, depositPct: c.depositPct, serviceOn: c.serviceOn === '1', notice: c.notice || '',
    firebase: { client: !!fb, serviceAccount: !!sa, project: fb ? JSON.parse(fb).projectId : null },
    reader: { paired: !!fcm, lastSeen: Number(c.readerSeen) || null },
    pairingCode: url.origin + '/pair#' + token,
    last24h: Object.fromEntries((recent.results || []).map(r => [r.status, r.n]))
  });
}

async function saveSettings(request, env) {
  const b = await request.json().catch(() => ({}));
  if (b.rate !== undefined) {
    const r = Number(b.rate);
    if (!(r > 0 && r < 100000)) return json({ error: 'rate', message: 'سعر الصرف غير صحيح.' }, 400);
    await setSetting(env, 'rate', r);
  }
  if (b.depositPct !== undefined) {
    const d = Number(b.depositPct);
    if (!(d >= 0 && d <= 100)) return json({ error: 'deposit', message: 'نسبة العربون بين 0 و100.' }, 400);
    await setSetting(env, 'depositPct', d);
  }
  if (b.serviceOn !== undefined) await setSetting(env, 'serviceOn', b.serviceOn ? '1' : '0');
  if (b.notice !== undefined) await setSetting(env, 'notice', String(b.notice).slice(0, 300));
  return adminSettings(env, new URL(request.url));
}

// Takes the two files from Firebase: google-services.json and the service-account key.
async function saveFirebase(request, env) {
  const b = await request.json().catch(() => ({}));
  if (b.googleServices) {
    let g;
    try { g = JSON.parse(b.googleServices); } catch (e) { return json({ error: 'gs', message: 'ملف google-services.json غير صالح.' }, 400); }
    const client = (g.client || []).find(c => c.client_info && c.client_info.android_client_info && c.client_info.android_client_info.package_name === 'com.rafelo.picks') || (g.client || [])[0];
    if (!client || !g.project_info) return json({ error: 'gs', message: 'لم أجد تطبيق com.rafelo.picks في الملف.' }, 400);
    const conf = {
      projectId: g.project_info.project_id,
      senderId: g.project_info.project_number,
      appId: client.client_info.mobilesdk_app_id,
      apiKey: (client.api_key && client.api_key[0] && client.api_key[0].current_key) || ''
    };
    if (!conf.projectId || !conf.appId || !conf.apiKey) return json({ error: 'gs', message: 'الملف ناقص.' }, 400);
    await setSetting(env, 'firebaseClient', JSON.stringify(conf));
  }
  if (b.serviceAccount) {
    let s;
    try { s = JSON.parse(b.serviceAccount); } catch (e) { return json({ error: 'sa', message: 'ملف المفتاح غير صالح.' }, 400); }
    if (!s.client_email || !s.private_key || !s.project_id) return json({ error: 'sa', message: 'ملف المفتاح ناقص.' }, 400);
    await setSetting(env, 'firebaseServiceAccount', JSON.stringify({ client_email: s.client_email, private_key: s.private_key, project_id: s.project_id }));
    await setSetting(env, 'fcmAccess', '');
  }
  return adminSettings(env, new URL(request.url));
}

async function resetPairing(env, url) {
  await setSetting(env, 'readerToken', randomId(24));
  await setSetting(env, 'readerFcm', '');
  return adminSettings(env, url);
}

async function testPush(env) {
  const r = await pushToReader(env, { ping: String(Date.now()) }).catch(e => ({ ok: false, error: String(e.message || e) }));
  return json(r, r.ok ? 200 : 502);
}

// ---------- reader phone ----------

async function isReader(request, env) {
  const t = bearer(request);
  const stored = await getSetting(env, 'readerToken');
  return !!stored && safeEqual(t, stored);
}

async function touchReader(env) { await setSetting(env, 'readerSeen', Date.now()); }

async function readerConfig(env) {
  await touchReader(env);
  const fb = await getSetting(env, 'firebaseClient');
  return json({ firebase: fb ? JSON.parse(fb) : null });
}

async function readerRegister(request, env) {
  const { fcmToken } = await request.json().catch(() => ({}));
  if (!fcmToken || String(fcmToken).length < 20) return json({ error: 'token' }, 400);
  await setSetting(env, 'readerFcm', String(fcmToken));
  await touchReader(env);
  return json({ ok: true });
}

async function readerPending(env) {
  await touchReader(env);
  const rows = await env.DB.prepare("SELECT id FROM jobs WHERE status IN ('pending','working') AND created > ? ORDER BY created LIMIT 10")
    .bind(Date.now() - 85 * 1000).all();
  return json({ jobs: (rows.results || []).map(r => r.id) });
}

async function readerJob(env, id) {
  await touchReader(env);
  const job = await env.DB.prepare('SELECT id, url, status FROM jobs WHERE id = ?').bind(id).first();
  if (!job) return json({ error: 'not-found' }, 404);
  if (job.status === 'pending') await env.DB.prepare("UPDATE jobs SET status = 'working', updated = ? WHERE id = ?").bind(Date.now(), id).run();
  return json({ id: job.id, url: job.url, status: job.status });
}

async function readerResult(request, env, id) {
  await touchReader(env);
  const b = await request.json().catch(() => ({}));
  const job = await env.DB.prepare('SELECT status FROM jobs WHERE id = ?').bind(id).first();
  if (!job) return json({ error: 'not-found' }, 404);
  if (b.error || !Array.isArray(b.items) || b.items.length === 0) {
    await env.DB.prepare("UPDATE jobs SET status = 'failed', error = ?, updated = ? WHERE id = ?")
      .bind(String(b.error || 'empty').slice(0, 200), Date.now(), id).run();
    return json({ ok: true });
  }
  const items = b.items.slice(0, 200).map(it => ({
    name: String(it.name || '').slice(0, 300), img: String(it.img || '').slice(0, 400), attr: String(it.attr || '').slice(0, 120),
    qty: num(it.qty) || 1, usd: num(it.usd), usdBefore: num(it.usdBefore), soldOut: !!it.soldOut
  }));
  await env.DB.prepare("UPDATE jobs SET status = 'done', result = ?, updated = ? WHERE id = ?").bind(JSON.stringify(items), Date.now(), id).run();
  const c = await getConfig(env);
  const p = priceItems(items, c);
  return json({ ok: true, totalIqd: p.totalIqd, totalUsd: p.totalUsd, count: p.count });
}

// ---------- Firebase Cloud Messaging ----------

async function pushToReader(env, data) {
  if (env.FAKE_PUSH === '1') return { ok: true }; // local testing only (.dev.vars)
  const fcm = await getSetting(env, 'readerFcm');
  const saText = await getSetting(env, 'firebaseServiceAccount');
  if (!fcm) return { ok: false, error: 'no-reader' };
  if (!saText) return { ok: false, error: 'no-service-account' };
  const sa = JSON.parse(saText);
  const access = await fcmAccessToken(env, sa);
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: 'POST',
    headers: { authorization: 'Bearer ' + access, 'content-type': 'application/json' },
    body: JSON.stringify({ message: { token: fcm, data, android: { priority: 'HIGH', ttl: '60s' } } })
  });
  if (!res.ok) return { ok: false, error: 'fcm ' + res.status + ' ' + (await res.text()).slice(0, 200) };
  return { ok: true };
}

async function fcmAccessToken(env, sa) {
  const cached = await getSetting(env, 'fcmAccess');
  if (cached) {
    const [exp, tok] = cached.split('|');
    if (Number(exp) > Date.now() + 60000) return tok;
  }
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const der = Uint8Array.from(atob(pem), ch => ch.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const sig = b64url(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(header + '.' + claims)));
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=' + header + '.' + claims + '.' + sig
  });
  const j = await res.json();
  if (!j.access_token) throw new Error('google auth failed: ' + JSON.stringify(j).slice(0, 200));
  await setSetting(env, 'fcmAccess', (Date.now() + (j.expires_in || 3600) * 1000) + '|' + j.access_token);
  return j.access_token;
}
